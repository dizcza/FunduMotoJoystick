package de.dizcza.fundu_moto_joystick.util

import android.content.Context
import android.content.SharedPreferences

object Utils {

    private fun getSharedPref(context: Context) : SharedPreferences {
        return context.getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun isInverseServoAngleNeeded(context: Context): Boolean {
        return getSharedPref(context).getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false)
    }

    fun isMediapipeEnabled(context: Context): Boolean {
        return getSharedPref(context).getBoolean(Constants.MEDIAPIPE_ENABLED_KEY, false)
    }
}
