package de.dizcza.fundu_moto_joystick.util

import android.content.Context
import android.widget.Toast

object ToastRefrain {
    const val REFRACTORY_PERIOD: Long = 2000 // ms
    private var lastShown: Long = 0
    fun showText(context: Context?, sequence: CharSequence?, duration: Int) {
        val tick = System.currentTimeMillis()
        if (tick > lastShown + REFRACTORY_PERIOD) {
            Toast.makeText(context, sequence, duration).show()
            lastShown = tick
        }
    }
}
