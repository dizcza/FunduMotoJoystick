package de.kai_morich.fundu_moto_joystick;

import java.util.ArrayList;
import java.util.List;

public class CommandParser {
    private static final float SCALE_SONAR_DIST = 3.0f;

    private final List<Byte> mCommand;
    private int mDoNotSkip = 0;
    private final SonarView mSonarView;

    public CommandParser(SonarView sonarView) {
        mSonarView = sonarView;
        mCommand = new ArrayList<>();
    }

    public void receive(byte[] data) {
        for (byte item : data) {
            if (mDoNotSkip > 0) {
                mCommand.add(item);
                mDoNotSkip--;
                continue;
            }
            switch (item) {
                case 'S':
                    // Servo + Sonar
                    mCommand.add(item);
                    mDoNotSkip = 2;
                    break;
                case '\r':
                    // ignore \r
                    break;
                case '\n':
                    process();
                    mCommand.clear();
                    break;
                default:
                    // RX failure or corrupted data. Skip.
                    mDoNotSkip = 0;
                    mCommand.clear();
                    break;
            }
        }
    }

    private void process() {
        if (mCommand.isEmpty()) {
            return;
        }
        switch (mCommand.get(0)) {
            case 'S':
                // Servo + Sonar
                byte servoAngle = mCommand.get(1);
                byte sonarDist = mCommand.get(2);
                float servoAngleNorm = (servoAngle + 90) / 180.f;
                float sonarDistNorm = (sonarDist & 0xFF) / (float) 0xFF;
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
