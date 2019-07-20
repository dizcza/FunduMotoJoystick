package de.dizcza.fundu_moto_joystick;

import android.content.Context;
import android.widget.Toast;

public class ToastRefrain {

    public static final long REFRACTORY_PERIOD = 2000;  // ms
    private static long lastShown = 0;

    public static void showText(Context context, CharSequence sequence, int duration) {
        long tick = System.currentTimeMillis();
        if (tick > lastShown + REFRACTORY_PERIOD) {
            Toast.makeText(context, sequence, duration).show();
            lastShown = tick;
        }
    }
}
