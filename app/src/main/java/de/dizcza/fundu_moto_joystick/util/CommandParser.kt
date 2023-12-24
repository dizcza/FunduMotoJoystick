package de.dizcza.fundu_moto_joystick.util

import android.content.Context
import de.dizcza.fundu_moto_joystick.fragment.SonarView

class CommandParser(private val mContext: Context?, private val mSonarView: SonarView?) {
    private val mCommand: StringBuilder

    init {
        mCommand = StringBuilder()
    }

    fun receive(data: ByteArray) {
        for (item in data) {
            when (item) {
                '\r'.code.toByte() -> {}
                '\n'.code.toByte() -> {
                    process()
                    mCommand.setLength(0)
                }

                else -> mCommand.append(Char(item.toUShort()))
            }
        }
    }

    private fun process() {
        if (mCommand.length == 0) {
            return
        }
        when (mCommand[0]) {
            'S' -> {
                //  S<is_target:1d>,<servo angle:3d>,<sonar dist:.3f>
                // angle in [-90, 90]
                // dist in [0.00, 1.00]
                if (mCommand.length != 12 || mCommand[2] != ',' || mCommand[6] != ',') {
                    // invalid packet
                    return
                }
                val isTarget: Int
                var servoAngle: Int
                var sonarDistNorm: Float
                try {
                    isTarget = mCommand.substring(1, 2).toInt()
                    servoAngle = mCommand.substring(3, 6).toInt()
                    if (Utils.isInverseServoAngleNeeded(mContext)) {
                        servoAngle = -servoAngle
                    }
                    sonarDistNorm = mCommand.substring(7).toFloat()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    return
                }
                val servoAngleNorm = (servoAngle + 90) / 180f
                if (sonarDistNorm > 1.0f) {
                    sonarDistNorm = 1.0f
                }
                val x = servoAngleNorm * mSonarView!!.width
                val y = (1.0f - sonarDistNorm) * mSonarView.height
                if (isTarget == 1) {
                    mSonarView.clear() // it also indicates range ping is finished
                    mSonarView.drawRect(x, y)
                } else {
                    mSonarView.drawCircle(x, y)
                }
            }
        }
    }
}
