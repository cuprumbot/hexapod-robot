package edu.galileo.innovation.hexapod.robot

import android.util.Log
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

private const val HORIZONTAL    = 0
private const val VERTICAL      = 1
private const val KNEE          = 2

private const val RIGHT_FRONT   = 0
private const val RIGHT_MID     = 1
private const val RIGHT_BACK    = 2
private const val LEFT_FRONT    = 3
private const val LEFT_MID      = 4
private const val LEFT_BACK     = 5

private const val VERTICAL_RIGHT_RISE       = 35.0
private const val VERTICAL_LEFT_RISE        = -35.0
private const val VERTICAL_RETURN_TO_BASE   = 0.0

private const val HORIZONTAL_TURN_CLOCKWISE = 30.0
private const val HORIZONTAL_TURN_COUNTERCW = -30.0
private const val HORIZONTAL_RIGHT_FORWARD  = 12.0
private const val HORIZONTAL_LEFT_FORWARD   = -12.0
private const val HORIZONTAL_RIGHT_AGGRO    = 45.0
private const val HORIZONTAL_LEFT_AGGRO     = -45.0
private const val HORIZONTAL_RETURN_TO_BASE = 0.0

private const val KNEE_BASE                 = 90.0
private const val DELAY_TURN                = 100
private const val DELAY_FORWARD             = 120

class ServoDoubleHat (rHat: ServoHat, lHat: ServoHat) {
    private var rightHat = rHat
    private var leftHat = lHat

    private var stored = false

    private var legs = arrayOf  (
                                    arrayOf(8, 9, 10),  // RIGHT FRONT
                                    arrayOf(4, 5, 6),   // RIGHT MID
                                    arrayOf(0, 1, 2),   // RIGHT BACK
                                    arrayOf(8, 9, 10),  // LEFT FRONT
                                    arrayOf(4, 5, 6),   // LEFT MID
                                    arrayOf(0, 1, 2)    // LEFT BACK
                                )

    // Base positions +/- offsets
    // Offsets caused by the position of the arms and screws of the servos
    private var horizontalBase = arrayOf(
                                            85.0-20.0,  // RIGHT FRONT (90-5)
                                            90.0+5.0,   // RIGHT MID
                                            110.0+5.0,  // RIGHT BACK (90+20)
                                            95.0+10.0,  // LEFT FRONT (90+5)
                                            90.0+15.0,  // LEFT MID
                                            70.0+5.0    // LEFT BACK (90-20)
                                        )

    // Right: lower to rise the spider (lower the leg)
    // Left: higher to rise the spider (lower the leg)
    // Offsets caused by the position of the arms and screws of the servos
    private var verticalBase = arrayOf(
                                            50.0+7.0,   // RIGHT FRONT
                                            50.0-2.0,   // RIGHT MID - REPLACEMENT SERVO
                                            50.0-9.0,   // RIGHT BACK
                                            130.0-4.0,  // LEFT FRONT
                                            130.0-9.0,  // LEFT MID - REPLACEMENT SERVO
                                            130.0+0.0   // LEFT BACK - REPLACEMENT SERVO
                                        )

    private var verticalStore = arrayOf(160.0, 160.0, 160.0, 20.0, 20.0, 20.0)
    private var kneeStore = arrayOf(178.0, 178.0, 178.0, 2.0, 2.0, 2.0)

    init {
        // Set knees
        for (i in 0..2) {
            leftHat.setAngle(legs[i+3][KNEE], KNEE_BASE)
            rightHat.setAngle(legs[i+0][KNEE], KNEE_BASE)
        }
        Log.d(TAG, "Robot ready!")
    }

    fun extendKnees () {
        launch {
            for (i in 0..2) {
                leftHat.setAngle(legs[i + 3][KNEE], KNEE_BASE)
                rightHat.setAngle(legs[i + 0][KNEE], KNEE_BASE)
            }
        }
    }

    /*
    fun turnClockwiseImproved () {
        launch {
            moveVerticalRLR(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            moveHorizontalRLR(HORIZONTAL_TURN_CLOCKWISE)
            delay(DELAY_TURN)
            moveVerticalRLR(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            moveVerticalLRL(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            moveHorizontalLRL(HORIZONTAL_TURN_CLOCKWISE)
            delay(DELAY_TURN)
            moveVerticalLRL(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            moveHorizontalRLR(HORIZONTAL_RETURN_TO_BASE)
            moveHorizontalLRL(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_TURN)
        }
    }
    */

    fun turnClockwise () {
        launch {
            if (stored) {
                extendKnees()
                stored = false
                delay(DELAY_TURN)
            }

            // Slightly rotate legs while they are on the ground
            moveHorizontalRLR(HORIZONTAL_TURN_CLOCKWISE)
            moveHorizontalLRL(HORIZONTAL_TURN_CLOCKWISE)
            delay(DELAY_TURN)

            // Raise three legs while returning them to base position
            moveVerticalRLR(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            delay(DELAY_TURN)
            moveHorizontalRLR(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Lower the three legs
            moveVerticalRLR(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Raise the other three legs while returning them to base position
            moveVerticalLRL(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            delay(DELAY_TURN)
            moveHorizontalLRL(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Lower the three legs
            moveVerticalLRL(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)
        }
    }

    fun turnCounterClockwise () {
        launch {
            if (stored) {
                extendKnees()
                stored = false
                delay(DELAY_TURN)
            }

            // Slightly rotate legs while they are on the ground
            moveHorizontalLRL(HORIZONTAL_TURN_COUNTERCW)
            moveHorizontalRLR(HORIZONTAL_TURN_COUNTERCW)
            delay(DELAY_TURN)

            // Raise three legs while returning them to base position
            moveVerticalLRL(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            delay(DELAY_TURN)
            moveHorizontalLRL(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Lower the three legs
            moveVerticalLRL(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Raise the other three legs while returning them to base position
            moveVerticalRLR(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
            delay(DELAY_TURN)
            moveHorizontalRLR(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_TURN)

            // Lower the three legs
            moveVerticalRLR(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_TURN)
        }
    }

    fun forward () {
        launch {
            if (stored) {
                extendKnees()
                stored = false
                delay(DELAY_TURN)
            }

            for (i in 1..5) {
                // Raise three legs and move them forward
                // The legs on the ground move backwards
                moveVerticalLRL(VERTICAL_RETURN_TO_BASE)
                //delay(DELAY_FORWARD)
                moveVerticalRLR(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
                delay(DELAY_FORWARD)
                moveHorizontalRLR(HORIZONTAL_LEFT_FORWARD, HORIZONTAL_RIGHT_FORWARD)
                moveHorizontalLRL(-HORIZONTAL_LEFT_FORWARD, -HORIZONTAL_RIGHT_FORWARD)
                delay(DELAY_FORWARD)

                // Raise the other three legs and move them forward
                // The legs on the ground move backwards
                moveVerticalRLR(VERTICAL_RETURN_TO_BASE)
                //delay(DELAY_FORWARD)
                moveVerticalLRL(VERTICAL_LEFT_RISE, VERTICAL_RIGHT_RISE)
                delay(DELAY_FORWARD)

                moveHorizontalLRL(HORIZONTAL_LEFT_FORWARD, HORIZONTAL_RIGHT_FORWARD)
                moveHorizontalRLR(-HORIZONTAL_LEFT_FORWARD, -HORIZONTAL_RIGHT_FORWARD)
                delay(DELAY_FORWARD)
            }

            standStill()
            delay(DELAY_FORWARD)
        }
    }

    fun standStill () {
        launch {
            if (stored) {
                extendKnees()
                stored = false
                delay(DELAY_TURN)
            }

            // Return all legs to base position
            moveHorizontalRLR(HORIZONTAL_RETURN_TO_BASE)
            moveHorizontalLRL(HORIZONTAL_RETURN_TO_BASE)
            delay(DELAY_FORWARD/2)
            moveVerticalRLR(VERTICAL_RETURN_TO_BASE)
            moveVerticalLRL(VERTICAL_RETURN_TO_BASE)
            delay(DELAY_FORWARD/2)
        }
    }

    fun store () {
        launch {
            // Raise the legs of the hexapod to store the robot
            leftHat.setAngle(legs[LEFT_FRONT][VERTICAL], verticalStore[LEFT_FRONT])
            delay(DELAY_FORWARD)
            leftHat.setAngle(legs[LEFT_MID][VERTICAL], 180.0 - verticalStore[LEFT_MID])
            delay(DELAY_FORWARD)
            leftHat.setAngle(legs[LEFT_BACK][VERTICAL], verticalStore[LEFT_BACK])
            delay(DELAY_FORWARD)
            rightHat.setAngle(legs[RIGHT_FRONT][VERTICAL], verticalStore[RIGHT_FRONT])
            rightHat.setAngle(legs[RIGHT_MID][VERTICAL], 180.0 - verticalStore[RIGHT_MID])
            rightHat.setAngle(legs[RIGHT_BACK][VERTICAL], 180.0 - verticalStore[RIGHT_BACK])
            delay(DELAY_FORWARD)

            // Bend the knees of the hexapod to store the robot
            for (i in 0..2) {
                leftHat.setAngle(legs[i+3][KNEE], kneeStore[i+3])
                rightHat.setAngle(legs[i+0][KNEE], kneeStore[i+0])
            }
            delay(DELAY_FORWARD)

            stored = true
        }
    }

    fun callibrate () {
        val TEST_RISE = 10.0

        launch {
            moveHorizontalRLR(HORIZONTAL_RETURN_TO_BASE)
            moveHorizontalLRL(HORIZONTAL_RETURN_TO_BASE)

            // Move the legs to their base position +/- some offset
            // Useful for aligning the legs (servo arms or horns may be in slightly different position)
            // Some servos have 180 - ANGLE because my replacement servos move in the opposite direction
            leftHat.setAngle(legs[LEFT_FRONT][VERTICAL], (verticalBase[LEFT_FRONT] - TEST_RISE))
            leftHat.setAngle(legs[LEFT_MID][VERTICAL], 180.0 - (verticalBase[LEFT_MID] - TEST_RISE))
            leftHat.setAngle(legs[LEFT_BACK][VERTICAL], (verticalBase[LEFT_BACK] - TEST_RISE))
            rightHat.setAngle(legs[RIGHT_FRONT][VERTICAL], (verticalBase[RIGHT_FRONT] + TEST_RISE))
            rightHat.setAngle(legs[RIGHT_MID][VERTICAL], 180.0 - (verticalBase[RIGHT_MID] + TEST_RISE))
            rightHat.setAngle(legs[RIGHT_BACK][VERTICAL], 180.0 - (verticalBase[RIGHT_BACK] + TEST_RISE))
        }
    }

    /*** EXERCISE EXTREME CAUTION WHEN MODIFYING THE FOLLOWING CODE. THE ROBOT MAY NOT MOVE PROPERLY. ***/

    // In the above code these functions are called with one argument to rotate the robot
    // Two arguments are used to walk.

    private fun moveHorizontalRLR (delta : Double) {moveHorizontalRLR(delta, delta)}
    private fun moveHorizontalRLR (deltaLeft : Double, deltaRight : Double) {
        rightHat.setAngle(legs[RIGHT_FRONT][HORIZONTAL], horizontalBase[RIGHT_FRONT] + deltaRight)
        leftHat.setAngle(legs[LEFT_MID][HORIZONTAL], horizontalBase[LEFT_MID] + deltaLeft)
        rightHat.setAngle(legs[RIGHT_BACK][HORIZONTAL], horizontalBase[RIGHT_BACK] + deltaRight)
    }

    private fun moveHorizontalLRL (delta : Double) {moveHorizontalLRL(delta, delta)}
    private fun moveHorizontalLRL (deltaLeft : Double, deltaRight : Double) {
        leftHat.setAngle(legs[LEFT_FRONT][HORIZONTAL], horizontalBase[LEFT_FRONT] + deltaLeft)
        rightHat.setAngle(legs[RIGHT_MID][HORIZONTAL], horizontalBase[RIGHT_MID] + deltaRight)
        leftHat.setAngle(legs[LEFT_BACK][HORIZONTAL], horizontalBase[LEFT_BACK] + deltaLeft)
    }

    // In the above code these functions are called with one argument (usually zero) to lower the legs
    // Two arguments are used to raise the legs (each side needs a different number to rise)

    private fun moveVerticalRLR (delta : Double) {moveVerticalRLR(delta, delta)}
    private fun moveVerticalRLR (deltaLeft : Double, deltaRight : Double) {
        rightHat.setAngle(legs[RIGHT_FRONT][VERTICAL], verticalBase[RIGHT_FRONT] + deltaRight)
        leftHat.setAngle(legs[LEFT_MID][VERTICAL], 180.0-(verticalBase[LEFT_MID] + deltaLeft))          // REPLACEMENT SERVO
        rightHat.setAngle(legs[RIGHT_BACK][VERTICAL], 180.0-(verticalBase[RIGHT_BACK] + deltaRight))    // REPLACEMENT SERVO
    }

    private fun moveVerticalLRL (delta : Double) {moveVerticalLRL(delta, delta)}
    private fun moveVerticalLRL (deltaLeft : Double, deltaRight : Double) {
        leftHat.setAngle(legs[LEFT_FRONT][VERTICAL], verticalBase[LEFT_FRONT] + deltaLeft)
        rightHat.setAngle(legs[RIGHT_MID][VERTICAL], 180.0-(verticalBase[RIGHT_MID] + deltaRight))      // REPLACEMENT SERVO
        leftHat.setAngle(legs[LEFT_BACK][VERTICAL], verticalBase[LEFT_BACK] + deltaLeft)
    }
}
