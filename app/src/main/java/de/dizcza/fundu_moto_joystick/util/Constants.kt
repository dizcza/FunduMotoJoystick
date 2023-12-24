package de.dizcza.fundu_moto_joystick.util

object Constants {
    // values have to be globally unique
    const val INTENT_ACTION_DISCONNECT = "de.dizcza.fundu_moto_joystick.Disconnect"
    const val NOTIFICATION_CHANNEL = "de.dizcza.fundu_moto_joystick.Channel"
    const val INTENT_CLASS_MAIN_ACTIVITY = "de.dizcza.fundu_moto_joystick.MainActivity"

    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001
    const val PREFERENCE_FILE_NAME = "FuduMoto_Prefence"
    const val INVERSE_SERVO_ANGLE_KEY = "InverseServoAngle"
    const val SONAR_MAX_DIST_KEY = "MaxSonarDist"
    const val SONAR_TOLERANCE_KEY = "SonarTolerance"
    const val SONAR_MEDIAN_FILTER_SIZE_KEY = "SonarMedianFilterSize"
    const val SONAR_TOLERANCE_DEFAULT = 1 // cm
    const val SONAR_MEDIAN_FILTER_SIZE_DEFAULT = 3
    const val BEEP_VOLUME_ALPHA_OFF = 0.4f
    const val BEEP_VOLUME_ALPHA_ON = 1.0f
    const val NEW_LINE = "\r\n"
}
