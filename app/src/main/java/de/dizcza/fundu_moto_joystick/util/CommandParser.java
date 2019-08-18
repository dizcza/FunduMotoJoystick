package de.dizcza.fundu_moto_joystick.util;

import android.content.Context;

import de.dizcza.fundu_moto_joystick.fragment.SonarView;

public class CommandParser {

    private final Context mContext;
    private final SonarView mSonarView;
    private final StringBuilder mCommand;

    public CommandParser(Context context, SonarView sonarView) {
        mContext = context;
        mSonarView = sonarView;
        mCommand = new StringBuilder();
    }

    public void receive(byte[] data) {
        for (byte item : data) {
            switch (item) {
                case '\r':
                    // ignore \r
                    break;
                case '\n':
                    process();
                    mCommand.setLength(0);
                    break;
                default:
                    mCommand.append((char) item);
                    break;
            }
        }
    }

    private void process() {
        if (mCommand.length() == 0) {
            return;
        }
        switch (mCommand.charAt(0)) {
            case 'S':
                //  S<is_target:1d>,<servo angle:3d>,<sonar dist:.3f>
                // angle in [-90, 90]
                // dist in [0.00, 1.00]
                if (mCommand.length() != 12 || mCommand.charAt(2) != ',' || mCommand.charAt(6) != ',') {
                    // invalid packet
                    return;
                }
                int isTarget;
                int servoAngle;
                float sonarDistNorm;
                try {
                    isTarget = Integer.parseInt(mCommand.substring(1, 2));
                    servoAngle = Integer.parseInt(mCommand.substring(3, 6));
                    if (Utils.isInverseServoAngleNeeded(mContext)) {
                        servoAngle = -servoAngle;
                    }
                    sonarDistNorm = Float.parseFloat(mCommand.substring(7));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return;
                }
                float servoAngleNorm = (servoAngle + 90) / 180.f;
                if (sonarDistNorm > 1.0f) {
                    sonarDistNorm = 1.0f;
                }
                float x = servoAngleNorm * mSonarView.getWidth();
                float y = (1.0f - sonarDistNorm) * mSonarView.getHeight();
                if (isTarget == 1) {
                    mSonarView.clear();  // it also indicates range ping is finished
                    mSonarView.drawRect(x, y);
                } else {
                    mSonarView.drawCircle(x, y);
                }
                break;
        }
    }

}
