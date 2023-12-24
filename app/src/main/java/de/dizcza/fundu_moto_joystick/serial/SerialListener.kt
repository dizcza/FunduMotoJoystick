package de.dizcza.fundu_moto_joystick.serial

interface SerialListener {
    fun onSerialConnect()
    fun onSerialConnectError(e: Exception)
    fun onSerialRead(data: ByteArray)
    fun onSerialIoError(e: Exception)
}
