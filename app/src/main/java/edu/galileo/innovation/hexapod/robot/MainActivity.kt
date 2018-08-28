package edu.galileo.innovation.hexapod.robot

// Basics
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.os.Handler
import android.os.HandlerThread
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
// Streaming
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import android.os.StrictMode

/*
    Hardware
 */
// I2C configs
const val I2C_DEVICE_NAME = "I2C1"
const val RIGHT_ADDRESS = 0x40
const val LEFT_ADDRESS = 0x42
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
/*
    Other
 */
const val TAG = "Main"
const val USING_FIREBASE = false

class MainActivity : Activity() {

    // Servos
    private var rightServoHat: ServoHat = ServoHat(RIGHT_ADDRESS)
    private var leftServoHat: ServoHat = ServoHat(LEFT_ADDRESS)
    private var twoHats: ServoDoubleHat = ServoDoubleHat(rightServoHat, leftServoHat)

    // Camera
    private lateinit var camera: CameraHelper
    private var cameraDelay = 1000L

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

    // Streaming
    private lateinit var socket: DatagramSocket
    private lateinit var packet: DatagramPacket
    private lateinit var address: InetAddress
    private var port = 0
    private var bufferSize = 255
    private var buffer = ByteArray(bufferSize)

    // Get from Firebase to control the robot
    private var takePictures = false
    private var moveDirection = "test"

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

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        if (!USING_FIREBASE) {
            cameraDelay = 300L

            Log.i(TAG, "Waiting for a datagram")
            // Streaming
            socket = DatagramSocket(4445)
            packet = DatagramPacket(buffer, bufferSize)

            socket.receive(packet)
            Log.i(TAG, "Got the starting datagram!")
            address = packet.address
            port = packet.port
            Log.d(TAG, "Address $address - Port: $port")
        }

        // Start the coroutine
        launch {
            delay(1000L)
            Log.i(TAG,"Starting!")
            moveServo()
        }

        Log.e(TAG, "After launching... (this shouldn't be seen)")
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
        val image = reader.acquireNextImage()
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

        if (USING_FIREBASE) {
            onPictureTaken(out)
        } else {
            onPictureTakenLocal(out)
        }
    }

    private fun onPictureTakenLocal(imageBytes: ByteArray) {
        if (imageBytes.size > 60000) { return }

        var dataPacket = DatagramPacket(imageBytes, imageBytes.size, address, port)
        //Log.d("PS", "Address: " + address.toString() + " Port: " + port)
        socket.send(dataPacket)

        Log.i("PS", "Packet sent! - Length: ${dataPacket.length}")
    }

    // Upload the image to Firebase
    private fun onPictureTaken(imageBytes: ByteArray) {
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
                Log.i(TAG, "Image upload successful! - Key: ${log.key}")

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
                Log.d(TAG, "Cloud vision annotations: $annotations")
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

        // If Firebase is being used: the phone app will control if pictures are being taken
        // If not being used: the robot will always take pictures
        if (takePictures || !USING_FIREBASE) {
            // Some small delays so the robot stops moving and the picture is still
            delay(cameraDelay)
            camera.takePicture()
            delay(cameraDelay)
        }

        when (moveDirection) {
            "forward" -> {
                twoHats.forward()
                delay(WALK_DELAY)
                twoHats.forward()
                delay(WALK_DELAY)
                twoHats.forward()
                delay(WALK_DELAY)
                Log.d(TAG, "forward")
            }
            "left" -> {
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                twoHats.turnCounterClockwise()
                delay(TURN_DELAY)
                Log.d(TAG, "left")
            }
            "right" -> {
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                twoHats.turnClockwise()
                delay(TURN_DELAY)
                Log.d(TAG, "right")
            }
            "stand" -> {
                twoHats.standStill()
                delay(WALK_DELAY)
                twoHats.standStill()
                delay(WALK_DELAY)
                twoHats.standStill()
                delay(WALK_DELAY)
                Log.d(TAG, "stand")
            }
            else -> {
                /*
                    TO DO: Check the knees of the robot before enabling the store() calls.
                 */
                twoHats.store()
                delay(WALK_DELAY)
            }
        }

        moveServo()
    }
}