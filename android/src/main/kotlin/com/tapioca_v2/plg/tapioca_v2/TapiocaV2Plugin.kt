package com.tapioca_v2.plg.tapioca_v2

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat
import com.daasuu.mp4compose.composer.Mp4Composer
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class TapiocaV2Plugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    PluginRegistry.RequestPermissionsResultListener, ActivityAware {

    private var activity: Activity? = null
    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null
    private var composer: Mp4Composer? = null
    private val myPermissionCode = 34264

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        setupChannels(binding.binaryMessenger)
    }

    private fun setupChannels(messenger: BinaryMessenger) {
        methodChannel = MethodChannel(messenger, "video_editor")
        eventChannel = EventChannel(messenger, "video_editor_progress")

        methodChannel?.setMethodCallHandler(this)

        eventChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }

            "writeVideofile" -> {
                val currentActivity = activity ?: run {
                    result.error("no_activity", "Activity is null", null)
                    return
                }
                val sink = eventSink ?: run {
                    result.error("no_event_sink", "Event sink is null", null)
                    return
                }

                checkPermission(currentActivity)

                val srcFilePath: String = call.argument("srcFilePath") ?: run {
                    result.error("src_file_path_not_found", "the src file path is not found.", null)
                    return
                }
                val destFilePath: String = call.argument("destFilePath") ?: run {
                    result.error("dest_file_path_not_found", "the dest file path is not found.", null)
                    return
                }
                val processing: HashMap<String, HashMap<String, Any>> = call.argument("processing") ?: run {
                    result.error("processing_data_not_found", "the processing is not found.", null)
                    return
                }

                composer = Mp4Composer(srcFilePath, destFilePath)
                val generator = VideoGeneratorService(composer!!)
                generator.writeVideofile(processing, result, currentActivity, sink)
            }

            "cancelExport" -> {
                composer?.let {
                    VideoGeneratorService(it).cancelExport(result)
                } ?: result.error("no_composer", "Composer is not initialized", null)
            }

            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel?.setMethodCallHandler(null)
        eventChannel?.setStreamHandler(null)
        methodChannel = null
        eventChannel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return requestCode == myPermissionCode
    }

    private fun checkPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            myPermissionCode
        )
    }
}
