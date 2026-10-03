package io.github.lethe_labs.argos.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Takes one still with the front camera, with no preview on screen. Blocking:
 * call it off the main thread. Returns the JPEG, or null if anything fails.
 */
object FrontCamera {
    /** Long enough for auto-exposure to settle, so the face is not black. */
    private const val WARMUP_MS = 800L
    private const val TIMEOUT_MS = 8_000L

    /** About 2 MP: enough to recognise a face, small enough to store many. */
    private const val MAX_PIXELS = 2_000_000

    @SuppressLint("MissingPermission") // The service only runs once CAMERA is granted.
    fun capture(context: Context): ByteArray? = runCatching {
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        } ?: return null
        val chars = manager.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val still = pick(map.getOutputSizes(ImageFormat.JPEG), MAX_PIXELS) ?: return null
        val warm = pick(map.getOutputSizes(SurfaceTexture::class.java), 640 * 480) ?: return null
        val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val thread = HandlerThread("argos-camera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(still.width, still.height, ImageFormat.JPEG, 1)
        val texture = SurfaceTexture(0).apply { setDefaultBufferSize(warm.width, warm.height) }
        val warmSurface = Surface(texture)
        val device = AtomicReference<CameraDevice?>(null)
        val result = AtomicReference<ByteArray?>(null)
        val done = CountDownLatch(1)

        reader.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { image ->
                val buffer = image.planes[0].buffer
                result.set(ByteArray(buffer.remaining()).also { buffer.get(it) })
            }
            done.countDown()
        }, handler)

        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device.set(camera)
                    @Suppress("DEPRECATION") // The list form is the one available on API 26.
                    camera.createCaptureSession(
                        listOf(warmSurface, reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                runCatching {
                                    val preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    preview.addTarget(warmSurface)
                                    session.setRepeatingRequest(preview.build(), null, handler)
                                }.onFailure { done.countDown() }
                                handler.postDelayed({
                                    runCatching {
                                        val shot = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                        shot.addTarget(reader.surface)
                                        // Phone held upright: the sensor orientation alone gives
                                        // an upright face.
                                        shot.set(CaptureRequest.JPEG_ORIENTATION, orientation)
                                        shot.set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                                        session.capture(shot.build(), null, handler)
                                    }.onFailure { done.countDown() }
                                }, WARMUP_MS)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) = done.countDown()
                        },
                        handler,
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    done.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    done.countDown()
                }
            }, handler)
            done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            runCatching { device.get()?.close() }
            reader.close()
            warmSurface.release()
            texture.release()
            thread.quitSafely()
        }
        result.get()
    }.getOrNull()

    /** The largest size within [maxPixels], or the smallest if none fits. */
    private fun pick(sizes: Array<Size>?, maxPixels: Int): Size? {
        if (sizes.isNullOrEmpty()) return null
        return sizes.filter { it.width * it.height <= maxPixels }.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
    }
}
