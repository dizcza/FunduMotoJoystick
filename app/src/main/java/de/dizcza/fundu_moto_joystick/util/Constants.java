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
    public static final String INVERSE_SERVO_ANGLE_KEY = "InverseServoAngle";
    public static final String SONAR_MAX_DIST_KEY = "MaxSonarDist";
    public static final String SONAR_TOLERANCE_KEY = "SonarTolerance";
    public static final String SONAR_MEDIAN_FILTER_SIZE_KEY = "SonarMedianFilterSize";

    public static final int SONAR_DIST_UPPERBOUND = 400;  // cm
    public static final int SONAR_DIST_LOWERBOUND = 10;  // cm
    public static final int SONAR_TOLERANCE_DEFAULT = 1; // cm
    public static final int SONAR_MEDIAN_FILTER_SIZE_DEFAULT = 5;

    public static final String NEW_LINE = "\r\n";

    private Constants() {}
}
