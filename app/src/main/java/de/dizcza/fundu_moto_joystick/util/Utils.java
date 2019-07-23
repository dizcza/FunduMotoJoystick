package de.dizcza.fundu_moto_joystick.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Utils {

    public static boolean isInverseServoAngleNeeded(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false);
    }
}
