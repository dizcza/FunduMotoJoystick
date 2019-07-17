package de.kai_morich.fundu_moto_joystick;

public class CommandParser {
    private static final float SCALE_SONAR_DIST = 3.0f;

    private final StringBuilder mCommand;
    private final SonarView mSonarView;

    public CommandParser(SonarView sonarView) {
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
                //  S<servo angle:3d>,<sonar dist:.2f>
                // angle in [-90, 90]
                // dist in [0.00, 1.00]
                if (mCommand.charAt(4) != ',') {
                    // invalid packet
                    return;
                }
                int servoAngle = Integer.parseInt(mCommand.substring(1, 4));
                float servoAngleNorm = (servoAngle + 90) / 180.f;
                float sonarDistNorm = Float.parseFloat(mCommand.substring(5));
                sonarDistNorm *= SCALE_SONAR_DIST;
                if (sonarDistNorm > 1.0f) {
                    sonarDistNorm = 1.0f;
                }
                float x = servoAngleNorm * mSonarView.getWidth();
                float y = (1.0f - sonarDistNorm) * mSonarView.getHeight();
                mSonarView.drawCircle(x, y);
                break;
        }
    }

}
