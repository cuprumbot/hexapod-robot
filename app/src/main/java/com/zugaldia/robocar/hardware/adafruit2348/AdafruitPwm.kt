
package com.zugaldia.robocar.hardware.adafruit2348

import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManagerService
import java.io.IOException

/**
 * A port of `Adafruit_PWM_Servo_Driver` (Adafruit PCA9685 16-Channel PWM Servo
 * Driver) to Android Things. Instead of using `Adafruit_I2C` we're using the
 * `I2cDevice` class shipped with Android Things.
 *
 *
 *
 * https://github.com/adafruit/Adafruit-Motor-HAT-Python-Library/blob/master/Adafruit_MotorHAT/Adafruit_PWM_Servo_Driver.py
 * https://developer.android.com/things/sdk/pio/i2c.html
 */

class AdafruitPwm (deviceName: String, address: Int) {
    private var i2c: I2cDevice? = null

    init {
        try {
            // Attempt to access the I2C device
            val manager = PeripheralManagerService()
            i2c = manager.openI2cDevice(deviceName, address)
        } catch (e: IOException) { }

        reset()
    }

    private fun reset() {
        setAllPwm(0, 0)
        writeRegByteWrapped(__MODE2, __OUTDRV.toByte())
        writeRegByteWrapped(__MODE1, __ALLCALL.toByte())
        sleepWrapped(0.005) // wait for oscillator

        var mode1 = readRegByteWrapped(__MODE1)
        mode1 = (mode1.toInt() and __SLEEP.inv()).toByte() // wake up (reset sleep)
        writeRegByteWrapped(__MODE1, mode1)
        sleepWrapped(0.005) // wait for oscillator
    }

    /**
     * Close the device.
     */
    fun close() {
        if (i2c != null) {
            try {
                i2c!!.close()
                i2c = null
            } catch (e: IOException) { }
        }
    }

    /**
     * Sets the PWM frequency.
     */
    fun setPwmFreq(freq: Int) {
        var prescaleval = 25000000.0f // 25MHz
        prescaleval /= 4096.0f // 12-bit
        prescaleval /= freq.toFloat()
        prescaleval -= 1.0f

        val prescale = Math.floor(prescaleval + 0.5)

        val oldmode = readRegByteWrapped(__MODE1)
        val newmode = (oldmode.toInt() and 0x7F or 0x10).toByte() // sleep
        writeRegByteWrapped(__MODE1, newmode) // go to sleep
        writeRegByteWrapped(__PRESCALE, Math.floor(prescale).toByte())
        writeRegByteWrapped(__MODE1, oldmode)
        sleepWrapped(0.005)
        writeRegByteWrapped(__MODE1, (oldmode.toInt() or 0x80).toByte())
    }

    /**
     * Sets a single PWM channel.
     */
    fun setPwm(channel: Int, on: Int, off: Int) {
        writeRegByteWrapped(__LED0_ON_L + 4 * channel, (on and 0xFF).toByte())
        writeRegByteWrapped(__LED0_ON_H + 4 * channel, (on shr 8).toByte())
        writeRegByteWrapped(__LED0_OFF_L + 4 * channel, (off and 0xFF).toByte())
        writeRegByteWrapped(__LED0_OFF_H + 4 * channel, (off shr 8).toByte())
    }

    /**
     * Sets a all PWM channels.
     */
    private fun setAllPwm(on: Int, off: Int) {
        writeRegByteWrapped(__ALL_LED_ON_L, (on and 0xFF).toByte())
        writeRegByteWrapped(__ALL_LED_ON_H, (on shr 8).toByte())
        writeRegByteWrapped(__ALL_LED_OFF_L, (off and 0xFF).toByte())
        writeRegByteWrapped(__ALL_LED_OFF_H, (off shr 8).toByte())
    }

    private fun sleepWrapped(seconds: Double) {
        try {
            Thread.sleep((seconds * 1000).toLong())
        } catch (e: InterruptedException) { }
    }

    private fun writeRegByteWrapped(reg: Int, data: Byte) {
        try {
            i2c!!.writeRegByte(reg, data)
        } catch (e: IOException) {
            return
        }
    }

    private fun readRegByteWrapped(reg: Int): Byte {
        var data: Byte = 0

        try {
            data = i2c!!.readRegByte(reg)
        } catch (e: IOException) { }

        return data
    }

    companion object {
        // Registers
        private const val __MODE1 = 0x00
        private const val __MODE2 = 0x01
        private const val __SUBADR1 = 0x02
        private const val __SUBADR2 = 0x03
        private const val __SUBADR3 = 0x04
        private const val __PRESCALE = 0xFE
        private const val __LED0_ON_L = 0x06
        private const val __LED0_ON_H = 0x07
        private const val __LED0_OFF_L = 0x08
        private const val __LED0_OFF_H = 0x09
        private const val __ALL_LED_ON_L = 0xFA
        private const val __ALL_LED_ON_H = 0xFB
        private const val __ALL_LED_OFF_L = 0xFC
        private const val __ALL_LED_OFF_H = 0xFD

        // Bits
        private const val __RESTART = 0x80
        private const val __SLEEP = 0x10
        private const val __ALLCALL = 0x01
        private const val __INVRT = 0x10
        private const val __OUTDRV = 0x04
    }
}