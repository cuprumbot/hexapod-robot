package edu.galileo.innovation.hexapod.robot

// Basics
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.os.Handler
import android.os.HandlerThread
// Hardware
import com.zugaldia.robocar.hardware.adafruit2348.AdafruitPwm
// Coroutines
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
// Camera
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ImageReader
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
// Firebase
import com.google.firebase.storage.FirebaseStorage
// Utils
import java.io.IOException
import java.nio.ByteBuffer

/*
    Hardware
 */
// I2C configs
const val I2C_DEVICE_NAME = "I2C1"
const val RIGHT_ADDRESS = 0x40
const val LEFT_ADDRESS = 0x41
// Servo configs
// Original     const val MIN_PULSE_MS = 1.0
// Raspi        const val MIN_PULSE_MS = 0.5
const val MIN_PULSE_MS = 0.5
const val MAX_PULSE_MS = 2.0
const val MIN_ANGLE_DEG = 0.0
const val MAX_ANGLE_DEG = 180.0
const val MIN_CHANNEL = 0
const val MAX_CHANNEL = 15
// Delays
const val TURN_DELAY = 600L
const val WALK_DELAY = 1000L

const val HW_TEST = false
const val TAG = "Main"

class MainActivity : Activity() {
    // Servos
    private var rightServoHat: ServoHat = ServoHat(RIGHT_ADDRESS)
    private var leftServoHat: ServoHat = ServoHat(LEFT_ADDRESS)
    private var twoHats: ServoDoubleHat = ServoDoubleHat(rightServoHat, leftServoHat)

    // Camera
    private lateinit var camera: CameraHelper
    // Firebase
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private lateinit var cameraState: DatabaseReference
    private lateinit var moveState: DatabaseReference
    // Handler and thread for camera
    private lateinit var cameraHandler: Handler
    private lateinit var cameraHandlerThread: HandlerThread
    // Handler and thread for Cloud Vision API
    private lateinit var cloudHandler: Handler
    private lateinit var cloudHandlerThread: HandlerThread

    private var takePictures = false
    private var moveDirection = "stand"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Camera permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Stop if got no permission
            // Remember to use an updated version of Android Studio, older versions may not grant the permission
            Log.e(TAG, "No permission")
            return
        }

        // Init Firebase
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        cameraState = FirebaseDatabase.getInstance().reference.child("camera")
        moveState = FirebaseDatabase.getInstance().reference.child("movement")

        // Thread for camera
        cameraHandlerThread = HandlerThread("CameraBackground")
        cameraHandlerThread.start()
        cameraHandler = Handler(cameraHandlerThread.looper)

        // Thread for Cloud Vision API
        cloudHandlerThread = HandlerThread("cloudHandlerThread")
        cloudHandlerThread.start()
        cloudHandler = Handler(cloudHandlerThread.looper)

        // Init camera
        camera = CameraHelper
        camera.initializeCamera(this, cameraHandler, onImageAvailableListener)

        val cameraStateListener = object: ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                takePictures = (dataSnapshot.child("state").value.toString() == "started")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "cameraStateListener.onCancelled()")
            }
        }
        cameraState.addValueEventListener(cameraStateListener)

        val moveStateListener = object: ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                moveDirection = dataSnapshot.child("state").value.toString()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "moveStateListener.onCancelled()")
            }
        }
        moveState.addValueEventListener(moveStateListener)

        // Start the coroutine
        launch {
            delay(1000L)
            Log.d(TAG,"Starting!")
            moveServo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Hardware
        rightServoHat.close()
        leftServoHat.close()

        // Camera and Firebase
        camera.shutDown()
        cameraHandlerThread.quitSafely()
        cloudHandlerThread.quitSafely()
    }

    // Listener for new images
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // Read the image
        val image = reader.acquireLatestImage()
        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)
        // If the image is not closed, an error will occur when capturing the next image
        image.close()

        /*
            Developer previews (and the release version I tried) have an error with the images.
            The size won't be recognized and a 6 MB buffer will be allocated.
            This is a problem when uploading it to the database, since it becomes way too slow.
            The next code fixes that, although in a slow way because of the file conversions.
         */

        // First convert the JPEG image that the camera captured to a BMP
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val bytes = bmp.byteCount
        val buffer = ByteBuffer.allocate(bytes)
        bmp.copyPixelsToBuffer(buffer)
        // Convert the image back to JPEG
        val outStream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
        // onPictureTaken() requires a byte array
        val out = outStream.toByteArray()
        onPictureTaken(out)
    }

    // Upload the image to Firebase
    private fun onPictureTaken(imageBytes: ByteArray?) {
        if (imageBytes != null) {
            // Two things will be saved, metadata in the database, and the image in storage
            val log = database.getReference("logs").push()
            val imageRef = storage.reference.child(log.key)

            // Reference for the newest image
            val newest = database.getReference("newest")

            // Upload image to storage
            val task = imageRef.putBytes(imageBytes)
            task.addOnSuccessListener { taskSnapshot ->
                // Mark image in the database
                val downloadUrl = taskSnapshot.downloadUrl
                log.child("timestamp").setValue(ServerValue.TIMESTAMP)
                log.child("image").setValue(downloadUrl?.toString())
                Log.i(TAG, "Image upload successful")
                Log.i(TAG, ">>>>> " + log.key + " <<<<<")

                // Update newest image
                val update = HashMap<String,Any>()
                update["url"] = downloadUrl.toString()
                update["name"] = log.key
                newest.updateChildren(update)

                // Process image annotations
                //annotateImage(log, imageBytes)
            }.addOnFailureListener {
                // Unable to store image, clean up the entry in the database
                log.removeValue()
                Log.e(TAG, "Unable to upload image to Firebase")
            }
        }
    }

    // Send the image to Cloud Vision API
    private fun annotateImage(ref: DatabaseReference, imageBytes: ByteArray) {
        // Run this in a separate thread since it might be the slowest operation
        // Since it is Android Things, there should be no problem with the user switching to another app
        cloudHandler.post(Runnable {
            Log.d(TAG, "Sending image to Cloud Vision API")
            // Annotate image by uploading to Cloud Vision API
            try {
                val annotations = CloudVisionUtils.annotateImage(imageBytes)
                Log.d(TAG, "cloud vision annotations:" + annotations!!)
                if (annotations != null) {
                    // Save the annotations in the database
                    ref.child("annotations").setValue(annotations)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Cloud Vision API error: ", e)
            }
        })
    }

    // Main suspend function
    // Make the robot walk and take pictures every time it stops
    private suspend fun moveServo(){

        if (takePictures) {
            // Some small delays so the picture is still
            delay(1000L)
            camera.takePicture()
            delay(1000L)
        }

        //Log.w(TAG, moveDirection)
        when (moveDirection) {
            "forward" -> {
                twoHats.forward()
                delay(WALK_DELAY)
                twoHats.forward()
                delay(WALK_DELAY)
                twoHats.forward()
                delay(WALK_DELAY)
                Log.w(TAG, moveDirection)
            }
            "left" -> {
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                Log.w(TAG, moveDirection)
            }
            "right" -> {
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                Log.w(TAG, moveDirection)
            }
            else -> {
                /*
                twoHats.standStill()
                delay(WALK_DELAY)
                twoHats.standStill()
                delay(WALK_DELAY)
                twoHats.standStill()
                delay(WALK_DELAY)
                Log.w(TAG, moveDirection)
                */
                twoHats.store()
                delay(WALK_DELAY)
                twoHats.store()
                delay(WALK_DELAY)
                twoHats.store()
                delay(WALK_DELAY)
            }
        }

        /*
        if (HW_TEST) {
            // test() takes the servos to their resting position
            twoHats.test()
        } else {
            // Put a cute walking routine here :^)
            twoHats.forward()
            delay(WALK_DELAY)
            twoHats.forward()
            delay(WALK_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)

            /*
            twoHats.forward()
            delay(WALK_DELAY)
            twoHats.forward()
            delay(WALK_DELAY)
            twoHats.forward()
            delay(WALK_DELAY)
            twoHats.forward()
            delay(WALK_DELAY)

            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            twoHats.turnCounterClockwise()
            delay(TURN_DELAY)
            */
        }
        */

        moveServo()
    }
}