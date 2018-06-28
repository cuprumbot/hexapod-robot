package edu.galileo.innovation.hexapod.robot

import com.zugaldia.robocar.hardware.adafruit2348.AdafruitPwm

class ServoHat(address : Int) {
    private val adafruitPWM: AdafruitPwm = AdafruitPwm(I2C_DEVICE_NAME, address)

    /*
     https://github.com/adafruit/Adafruit_Python_PCA9685/blob/master/examples/simpletest.py
     1,000,000 us per second
     40 Hz
     12 bits of resolution
    */

    // Original     private val pulseLength = (1000000 / 40) / 4096
    // Raspi        private val pulseLength = (1000000 / 50) / 4096

    private val pulseLength = (1000000 / 50) / 4096

    private val servos: HashMap<Int, Double> = HashMap()
    init {
        // Raspi
        adafruitPWM.setPwmFreq(50)
        for (channel in 1..15) {
            servos[channel] = 0.0
        }
    }

    fun setAngle(channel: Int, angle: Double) {
        if (angle in MIN_ANGLE_DEG..MAX_ANGLE_DEG && channel in MIN_CHANNEL..MAX_CHANNEL) {
            val normalizedAngleRatio = (angle - MIN_ANGLE_DEG) / (MAX_ANGLE_DEG - MIN_ANGLE_DEG)
            val pulse = MIN_PULSE_MS + (MAX_PULSE_MS - MIN_PULSE_MS) * normalizedAngleRatio
            val dutyCycle = (pulse * 1000 / pulseLength).toInt()
            servos[channel] = angle
            adafruitPWM.setPwm(channel, 0, dutyCycle)
        }
    }

    fun getAngle(channel: Int): Double {
        var angle = 0.0
        if (channel in MIN_CHANNEL..MAX_CHANNEL) {
            servos[channel]?.let{
                angle = it
            }
        }
        return angle
    }

    fun close() {
        adafruitPWM.close()
    }
}