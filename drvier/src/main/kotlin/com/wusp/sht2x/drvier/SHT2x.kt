package com.wusp.sht2x.drvier

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManagerService
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.withTimeout
import java.io.IOException
import kotlin.experimental.and

/**
 * Check sum mechanism use CRC-8
 * Created by wusp on 2017/9/25.
 */
class SHT2x(val bus: String) : AutoCloseable {
    private val device: I2cDevice? = PeripheralManagerService().openI2cDevice(bus, I2C_ADDRESS)
    private var userState: UserRegister = UserRegister()
    private val ht: HandlerThread = HandlerThread("SHT2x")
    private var mHandler: Handler

    init {
        ht.start()
        mHandler = Handler(ht.looper)
    }

    companion object {
        val TAG: String = SHT2x::class.java.simpleName
        val I2C_ADDRESS: Int = 0x40
        val RH_RESOLUTION_LOW: Int = 8
        val RH_RESOLUTION_HIGH: Int = 12
        val T_RESOLUTION_LOW: Int = 12
        val T_RESOLUTION_HIGH: Int = 14
        val RH_RANGE_LOW: Int = 0
        val RH_RANGE_HIGH: Int = 100
        val T_RANGE_LOW: Int = -40
        val T_RANGE_HIGH: Int = 125
        //Times' units are all in ms.
        val MEASUREMENT_TIMES_T_RESOLUTION_LOW: Int = 22
        val MEASUREMENT_TIMES_T_RESOLUTION_HIGH: Int = 85
        val MEASUREMENT_TIMES_RH_RESOLUTION_LOW: Int = 3
        val MEASUREMENT_TIMES_RH_RESOLUTION_HIGH: Int = 22
        val DEFAULT_USER_REGISTER: Int = 0x02
        val ACTION_READ_MEASUREMENT = 0x31
    }

    enum class COMMAND(val code: Int) {
        TRIGGER_T_MEASUREMENT_HOLD(0xE3),
        TRIGGER_RH_MEASUREMENT_HOLD(0xE5),
        TRIGGER_T_MEASUREMENT_NOT_HOLD(0xF3),
        TRIGGER_RH_MEASUREMENT_NOT_HOLD(0xF5),
        WRITE_USER_REGISTER(0xE6),
        READ_USER_REGISTER(0xE7),
        SOFT_RESET(0xFE);
    }

    class UserRegister(var mState: Byte = DEFAULT_USER_REGISTER.toByte()) {
        companion object {
            val OC_HEATER_ENABLED: Byte = 0x01.shl(2)
            val VDD_OVER_2V25: Byte = 0x01.shl(6)
            //Use 7 bit + 0 bit to describe RESOLUTION definition
            val RESOLUTION_HIGH: Byte = 0x00   //RH - 12, T - 14
            val RESOLUTION_LOW: Byte = 0x01    //RH - 8, T - 12
        }

        fun heaterEnabled(): Boolean {
            return mState.and(OC_HEATER_ENABLED) > 0
        }

        fun highResolution(): Boolean {
            return (mState.and(0x80.toByte()) + mState.and(0x01.toByte())).toByte().and(RESOLUTION_HIGH) > 0
        }

        fun lowResolution(): Boolean {
            return (mState.and(0x80.toByte()) + mState.and(0x01.toByte())).toByte().and(RESOLUTION_LOW) > 0
        }

        fun vddOver2V25(): Boolean {
            return mState.and(VDD_OVER_2V25) > 0
        }

        fun setResolutionHigh() {
            mState = (mState.toInt() or RESOLUTION_HIGH.toInt()).toByte()
        }

        fun setResolutionLow() {
            mState = (mState.toInt() or RESOLUTION_LOW.toInt()).toByte()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        device?.close()
    }

    /**
     * Set the sensor to use High-Resolution to measure
     *
     * @high true if use high-resolution, or false to use low-resolution
     */
    fun setSensorResolution(high: Boolean) {
        if (high) userState.setResolutionHigh() else userState.setResolutionLow()
        device?.writeRegWord(I2C_ADDRESS,
                (COMMAND.WRITE_USER_REGISTER.code and 0xFF shl 8 or (userState.mState and 0xFF.toByte()).toInt()).toShort())
    }

    fun readUserRegister() {
        device?.write(byteArrayOf(0b11100111.toByte()), 1)
        val regState = ByteArray(1)
        device?.read(regState, 1)
        userState.mState = regState[0]  //adjust userState's bit wise
    }

    /**
     * Send command to reboot the sensor system without switching the power off and on again.
     */
    fun softReset() {
        val ba = ByteArray(1, { COMMAND.SOFT_RESET.code.toByte() })
        device?.write(ba, 1)
    }

    /**
     * Calling thread would be blocked, so it's practical to work with kotlinx.coroutines
     *
     * coroutine API with Timeout mechanism
     */
    @Throws(CancellationException::class)
    suspend fun readTinNotHold(): ByteArray? = withTimeout(2000L) {
        val b = arrayOf(COMMAND.TRIGGER_T_MEASUREMENT_NOT_HOLD.code.toByte()).toByteArray()
        val result = triggerNotHoldingMeasurement(b, 100) ?: return@withTimeout null
        if (responseCompleteness(result) && responseCorrectness(result, COMMAND.TRIGGER_T_MEASUREMENT_NOT_HOLD)) {
            return@withTimeout datasAdjust(result)
        } else {
            return@withTimeout null
        }
    }

    @Throws(CancellationException::class)
    suspend fun readTinHold(): ByteArray? = withTimeout(2000L) {
        val b = arrayOf(COMMAND.TRIGGER_T_MEASUREMENT_HOLD.code.toByte()).toByteArray()
        val result = triggerHoldingMeasurement(b) ?: return@withTimeout null
        if (responseCompleteness(result) && responseCorrectness(result, COMMAND.TRIGGER_T_MEASUREMENT_HOLD)) {
            return@withTimeout datasAdjust(result)
        } else {
            return@withTimeout null
        }
    }

    suspend fun readRHinHold(): ByteArray? = withTimeout(2000L) {
        val b = arrayOf(COMMAND.TRIGGER_RH_MEASUREMENT_HOLD.code.toByte()).toByteArray()
        val result = triggerHoldingMeasurement(b) ?: return@withTimeout null
        if (responseCompleteness(result) && responseCorrectness(result, COMMAND.TRIGGER_RH_MEASUREMENT_HOLD)) {
            return@withTimeout datasAdjust(result)
        } else {
            return@withTimeout null
        }
    }

    suspend fun readRHinNotHold(): ByteArray? = withTimeout(2000L) {
        val b = arrayOf(COMMAND.TRIGGER_RH_MEASUREMENT_NOT_HOLD.code.toByte()).toByteArray()
        val result = triggerNotHoldingMeasurement(b, 100) ?: return@withTimeout null
        if (responseCompleteness(result) && responseCorrectness(result, COMMAND.TRIGGER_RH_MEASUREMENT_NOT_HOLD)) {
            return@withTimeout datasAdjust(result)
        } else {
            return@withTimeout null
        }
    }

    /**
     * calling this two method to send command byte to sensor in order to trigger measurement,
     * then read the response byte from sensor.
     */
    private suspend fun triggerHoldingMeasurement(command: ByteArray): ByteArray? {
        if (device == null) return null
        val result = ByteArray(3)
        device.write(command, 1)
        device.read(result, 3)
        return result
    }

    private suspend fun triggerNotHoldingMeasurement(command: ByteArray, delay: Long): ByteArray? {
        if (device == null) return null
        val result = ByteArray(3)
        device.write(command, 1)
        kotlinx.coroutines.experimental.delay(delay)
        device.read(result, 3)
        return result
    }

    /**
     * 1.
     * Used to check the sensor measuring response should contain with MSB + LSB + CHECKSUM
     */
    private fun responseCompleteness(datas: ByteArray): Boolean {
        if (datas.isEmpty() || datas.size != 3) {
            if (datas.isEmpty()) {
                Log.e(TAG, "response is empty.")
            } else {
                Log.e(TAG, "response.size is not 3")
            }
            return false
        }
        return true
    }

    /**
     * 2.
     * Check whether the response is exactly what the command request is waiting for, and the data
     * can match the crc checksum.
     */
    private fun responseCorrectness(response: ByteArray, command: COMMAND): Boolean {
        val lsbMatchBit = 0b00000010
        if (command == COMMAND.TRIGGER_RH_MEASUREMENT_NOT_HOLD
                || command == COMMAND.TRIGGER_RH_MEASUREMENT_HOLD
                && response[1].and(lsbMatchBit.toByte()) == 0x00.toByte()) {
            Log.e(TAG, "RH Command bit not match.")
            return false
        } else if (command == COMMAND.TRIGGER_T_MEASUREMENT_NOT_HOLD
                || command == COMMAND.TRIGGER_T_MEASUREMENT_HOLD
                && response[1].and(lsbMatchBit.toByte()) != 0x00.toByte()) {
            Log.e(TAG, "T Command bit not match.")
            return false
        }
        val checksum = crc8maxim(intArrayOf(response[0].toInt(), response[1].toInt()))
        Log.e(TAG, "match result: " + (checksum.toByte() == response[2]))
        return checksum != null && checksum.toByte() == response[2]
    }

    /**
     * Calling this method to return the only data which would has adjusted the status value bit()
     */
    private fun datasAdjust(response: ByteArray): ByteArray {
        val rollOutBit = 0b11111100
        val lsb = response[1]
        return byteArrayOf(response[0], lsb.and(rollOutBit.toByte()))
    }
}