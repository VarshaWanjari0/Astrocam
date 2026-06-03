package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.max

class AstroCamera(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // State for UI
    var iso = mutableStateOf(800)
    var exposureTimeNs = mutableStateOf(1000000000L) // 1 second
    var focusDistance = mutableStateOf(0.0f) // Infinity
    var timerSeconds = mutableStateOf(3)
    var stackFrames = mutableStateOf(5)
    var capturing = mutableStateOf(false)
    var status = mutableStateOf("Ready")

    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var activeCameraId: String? = null
    private var supportsRaw = false

    fun startBackgroundThread() {
        handlerThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(handlerThread!!.looper)
    }

    fun stopBackgroundThread() {
        handlerThread?.quitSafely()
        try {
            handlerThread?.join()
            handlerThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            
            activeCameraId = cameraId
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            
            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
            supportsRaw = !rawSizes.isNullOrEmpty()
            
            val captureSize = if (supportsRaw) {
                rawSizes.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)
            } else {
                map.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)
            }
            
            val format = if (supportsRaw) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
            imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, format, stackFrames.value + 2)
            
            surfaceTexture.setDefaultBufferSize(width, height)
            previewSurface = Surface(surfaceTexture)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e("AstroCam", "Error opening camera", e)
        }
    }

    private fun createCameraPreviewSession() {
        val camera = cameraDevice ?: return
        val surface = previewSurface ?: return
        val readerSurface = imageReader?.surface ?: return

        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)

            camera.createCaptureSession(
                listOf(surface, readerSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            applyManualSettings(builder)
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        status.value = "Failed to configure"
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updatePreview() {
        val session = captureSession ?: return
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
            builder.addTarget(previewSurface!!)
            applyManualSettings(builder)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyManualSettings(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso.value)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs.value)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance.value)
    }

    fun captureAstro() {
        if (capturing.value) return
        capturing.value = true
        
        scope.launch {
            for (i in timerSeconds.value downTo 1) {
                status.value = "Timer: $i"
                delay(1000)
            }
            
            val totalFrames = stackFrames.value
            val images = mutableListOf<Image>()
            val captureResultObj = mutableListOf<CaptureResult>()
            
            status.value = "Capturing 1/$totalFrames..."
            
            var framesCaptured = 0
            
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                if (image != null) {
                    images.add(image)
                    framesCaptured++
                    status.value = "Capturing $framesCaptured/$totalFrames..."
                    if (framesCaptured >= totalFrames) {
                        reader.setOnImageAvailableListener(null, null)
                        processAndSave(images, captureResultObj.firstOrNull())
                    }
                }
            }, backgroundHandler)
            
            try {
                val requests = mutableListOf<CaptureRequest>()
                for(i in 0 until totalFrames) {
                    val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)!!
                    builder.addTarget(imageReader!!.surface)
                    applyManualSettings(builder)
                    requests.add(builder.build())
                }
                
                captureSession?.captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        if (captureResultObj.isEmpty()) captureResultObj.add(result)
                    }
                }, backgroundHandler)
            } catch(e: Exception) {
                e.printStackTrace()
                capturing.value = false
                status.value = "Capture error"
            }
        }
    }

    private fun processAndSave(images: List<Image>, captureResult: CaptureResult?) {
        status.value = "Stacking..."
        scope.launch(Dispatchers.Default) {
            try {
                if (images.isEmpty()) {
                    capturing.value = false
                    status.value = "No images"
                    return@launch
                }
                
                val width = images[0].width
                val height = images[0].height
                
                if (supportsRaw && captureResult != null) {
                    // Primitive RAW stacking: Hot pixel + Average
                    val totalPixels = width * height
                    val accum = LongArray(totalPixels)
                    
                    for (image in images) {
                        val buffer = image.planes[0].buffer
                        buffer.rewind()
                        val shortBuffer = buffer.asShortBuffer()
                        for (i in 0 until totalPixels) {
                            accum[i] += (shortBuffer.get(i).toLong() and 0xFFFF)
                        }
                        image.close()
                    }
                    
                    val averaged = ShortArray(totalPixels)
                    val frames = images.size
                    for (i in 0 until totalPixels) {
                        averaged[i] = (accum[i] / frames).toShort()
                    }
                    
                    // Simple Hot Pixel Removal
                    status.value = "Removing Hot Pixels..."
                    for (y in 1 until height - 1) {
                        for (x in 1 until width - 1) {
                            val idx = y * width + x
                            val p = averaged[idx].toInt() and 0xFFFF
                            
                            var localSum = 0
                            for(dy in -1..1) {
                                for(dx in -1..1) {
                                    if (dx==0 && dy==0) continue
                                    localSum += (averaged[(y+dy)*width + (x+dx)].toInt() and 0xFFFF)
                                }
                            }
                            val localAvg = localSum / 8
                            if (p > localAvg * 1.5 && p > 4000) { // arbitrary threshold
                                averaged[idx] = localAvg.toShort()
                            }
                        }
                    }
                    
                    status.value = "Saving DNG..."
                    val byteBuffer = ByteBuffer.allocate(totalPixels * 2)
                    byteBuffer.asShortBuffer().put(averaged)
                    
                    val chars = cameraManager.getCameraCharacteristics(activeCameraId!!)
                    val dngCreator = DngCreator(chars, captureResult)
                    val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    val outFile = File(outDir, "AstroCam_${System.currentTimeMillis()}.dng")
                    FileOutputStream(outFile).use { out ->
                        dngCreator.writeInputStream(out, Size(width, height), ByteArrayInputStream(byteBuffer.array()), 0)
                    }
                    status.value = "Saved to DCIM"
                } else {
                    // JPEG handling fallback (just save the first one for now)
                    status.value = "Saving JPEG..."
                    val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    val outFile = File(outDir, "AstroCam_${System.currentTimeMillis()}.jpg")
                    
                    val buffer = images[0].planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    FileOutputStream(outFile).write(bytes)
                    
                    images.forEach { it.close() }
                    status.value = "Saved JPEG (RAW not supported)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                status.value = "Error: \${e.message}"
            } finally {
                capturing.value = false
            }
        }
    }
}
