package de.dizcza.fundu_moto_joystick.util;

import de.dizcza.fundu_moto_joystick.BuildConfig;

public class Constants {

    // values have to be globally unique
    public static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    public static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    public static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    public static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    public static final String PREFERENCE_FILE_NAME = "FuduMoto_Prefence";
    public static final String INVERSE_SERVO_ANGLE = "InverseServoAngle";

    public static final int SERVO_ANGLE_UPPERBOUND = 400;  // cm
    public static final int SERVO_ANGLE_LOWERBOUND = 10;  // cm

    private Constants() {}
}
