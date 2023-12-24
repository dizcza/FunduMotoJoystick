package de.dizcza.fundu_moto_joystick.util

import android.content.Context

object Utils {
    fun isInverseServoAngleNeeded(context: Context?): Boolean {
        val sharedPref =
            context!!.getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false)
    }
}
