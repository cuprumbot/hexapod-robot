package edu.galileo.innovation.hexapod.robot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

object CameraHelper {
    private const val TAG = "CameraHelper"
    private const val IMAGE_HEIGHT = 240
    private const val IMAGE_WIDTH = 320
    private const val MAX_IMAGES = 1

    private var imageReader : ImageReader? = null
    private var cameraDevice : CameraDevice? = null
    private var cameraCaptureSession : CameraCaptureSession? = null

    /*
        Trying to use the camera from a coroutine gives the error: "No handler given, and current thread has no looper!"
        It was solved by using the post method of Handler.
        GET FEEDBACK ON THIS: Since it's Android Things and the user can't switch to another app, I guess there is no risk.
     */
    private lateinit var cameraHandler : Handler
    private lateinit var cameraHandlerThread : HandlerThread

    fun initializeCamera(context: Context,
                         bgHandler: Handler,
                         imgAvailableListener: ImageReader.OnImageAvailableListener) {

        // Discover the camera instance
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camIds = arrayOf<String>()

        try {
            camIds = manager.cameraIdList
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Cam access exception getting IDs", e)
        }

        if (camIds.isEmpty()) {
            Log.e(TAG, "No cameras found")
            return
        }

        val id = camIds[0]
        Log.d(TAG, "Using camera id $id")

        // Create the imageReader
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, MAX_IMAGES)
        imageReader?.setOnImageAvailableListener(imgAvailableListener, bgHandler)

        // Start a thread to be able to use the camera from a coroutine
        cameraHandlerThread = HandlerThread("CameraInternalThread")
        cameraHandlerThread.start()
        cameraHandler = Handler(cameraHandlerThread.looper)

        // Open the camera resource
        try {
            manager.openCamera(id, stateCallback, bgHandler)
        } catch (cae: CameraAccessException) {
            Log.e(TAG, "Camera access exception", cae)
        }
    }

    fun shutDown() {
        cameraDevice?.close()
    }

    fun takePicture() {
        // Sanity check
        if (cameraDevice == null) {
            Log.e(TAG, "Cannot capture image. Camera not initialized.")
            return
        }

        /*
            Magic.
            Using post() so createCaptureSession() doesn't die when using coroutines.
         */
        cameraHandler.post({
            try {
                cameraDevice?.let {
                    Log.v(TAG, "Starting: createCaptureSession()")
                    it.createCaptureSession(listOf(imageReader?.surface),
                                            sessionCallback,
                                            null)
                    Log.v(TAG, "Finished: createCaptureSession()")
                }
            } catch (e : Exception) {
                Log.e(TAG, "Fatal exception on takePicture(), when doing createCaptureSession().\n\n" + e.printStackTrace() + "\n\nEND OF STACK TRACE")
            }
        })
    }

    fun triggerImageCapture() {
        try {
            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(imageReader?.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            Log.d(TAG, "Capture session initialized.")
            cameraCaptureSession?.capture(captureBuilder?.build(), captureCallback, null)
        } catch (cae : CameraAccessException) {
            Log.e(TAG, "camera capture exception", cae)
        }
    }

    /*** CALLBACKS ***/

    private var stateCallback : CameraDevice.StateCallback =
        object : CameraDevice.StateCallback () {

            override fun onOpened(camDevice: CameraDevice?) {
                Log.d(TAG, "Opened camera.")
                cameraDevice = camDevice
            }

            override fun onDisconnected(camDevice: CameraDevice?) {
                Log.d(TAG, "Camera disconnected, closing.")
                camDevice?.close()
                cameraCaptureSession?.close()
                cameraCaptureSession = null
            }

            override fun onError(cameraDevice: CameraDevice?, i: Int) {
                Log.e(TAG, "Camera device error, closing.")
                cameraDevice?.close()
                cameraCaptureSession?.close()
                cameraCaptureSession = null
            }

            override fun onClosed(camDevice: CameraDevice?) {
                Log.d(TAG, "Closed camera, releasing.")
                cameraDevice = null
            }
        }

    private var sessionCallback : CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(camCaptureSession: CameraCaptureSession?) {
               cameraDevice?.let {
                   cameraCaptureSession = camCaptureSession
                   triggerImageCapture()
               }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession?) {
                Log.e(TAG, "Failed to configure camera.")
            }
        }

    private var captureCallback : CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureProgressed(cameraCaptureSession: CameraCaptureSession?,
                                             captureRequest: CaptureRequest?,
                                             captureResult: CaptureResult?) {
                Log.i(TAG, "Partial result (this message usually is not displayed, check what is happening).")
            }

            override fun onCaptureCompleted(camCaptureSession: CameraCaptureSession?,
                                            captureRequest: CaptureRequest?,
                                            totalCaptureResult: TotalCaptureResult?) {
                super.onCaptureCompleted(camCaptureSession, captureRequest, totalCaptureResult)

                if (cameraCaptureSession != null) {
                    cameraCaptureSession?.close()
                    cameraCaptureSession = null
                    Log.d(TAG, "Capture session closed")
                }
            }
        }
}