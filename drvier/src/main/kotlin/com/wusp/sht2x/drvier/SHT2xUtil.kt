package com.wusp.sht2x.drvier

/**
 * Created by wusp on 2017/9/26.
 */
//accoring to doc. P(x)=x^8+x^5+x^4+1 = 100110001 is CRC8-MAXIM
fun crc8maxim(datas: IntArray): Int? {
    if (datas.isEmpty()) return null
    val poly = 0x131 //100110001
    var crc = 0x00 //Doc. initialization is 0
    datas.forEach { data ->
        crc = crc.xor(data)
        for (it in 1..8) crc = if (crc > 0x80) (crc.shl(1).xor(poly)) else crc.shl(1)
    }
    return crc
}

/**
 * Convert the sensor response raw data to humidity data,
 * the input data set should already be rolled out status bits.
 *
 * return -1 if error occurs.
 */
fun convertToHumidity(datas: ByteArray): Float {
    if (datas.isEmpty()) return -1f
    var rawData: Float = (datas[0].toInt().shl(8) + datas[1]).toFloat()
    rawData *= 125
    rawData /= 2.shl(15)
    return rawData - 6
}

/**
 * Convert the sensor response raw data to temperature data,
 * the input data set should already be rolled out status bit.
 *
 * return -1 if error occurs.
 */
fun convertToTemperature(datas: ByteArray): Float {
    if (datas.isEmpty()) return -1f
    var rawData: Float = (datas[0].toInt().shl(8) + datas[1]).toFloat()
    rawData *= 175.72f
    rawData /= 2.shl(15)
    return rawData - 46.85f
}
