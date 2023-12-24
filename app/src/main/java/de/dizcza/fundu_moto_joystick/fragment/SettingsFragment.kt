package de.dizcza.fundu_moto_joystick.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.dizcza.fundu_moto_joystick.OnBackPressed
import de.dizcza.fundu_moto_joystick.R
import de.dizcza.fundu_moto_joystick.util.Constants

class SettingsFragment : Fragment(), OnBackPressed {
    private var mInverseServo: CheckBox? = null
    private var mSonarMaxDist: SeekBar? = null
    private var mSonarTolerance: SeekBar? = null
    private var mSonarMedianFilterSize: SeekBar? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    private fun bindSeekBar(seekBar: SeekBar?, textView: TextView) {
        seekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textView.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.settings, container, false)
        mInverseServo = view.findViewById(R.id.invert_servo)
        val sharedPref =
            requireContext().getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val inverseAngle = sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false)
        mInverseServo!!.isChecked = inverseAngle
        mSonarMaxDist = view.findViewById(R.id.max_sonar_dist)
        bindSeekBar(mSonarMaxDist, view.findViewById(R.id.max_sonar_dist_text))
        val upperbound = resources.getInteger(R.integer.sonar_max_dist_lowerbound)
        val maxDist = sharedPref.getInt(Constants.SONAR_MAX_DIST_KEY, upperbound)
        mSonarMaxDist!!.progress = maxDist
        mSonarTolerance = view.findViewById(R.id.sonar_tolerance)
        bindSeekBar(mSonarTolerance, view.findViewById(R.id.sonar_tolerance_text))
        val toleranceCm =
            sharedPref.getInt(Constants.SONAR_TOLERANCE_KEY, Constants.SONAR_TOLERANCE_DEFAULT)
        mSonarTolerance!!.progress = toleranceCm
        mSonarMedianFilterSize = view.findViewById(R.id.sonar_median_filter_size)
        bindSeekBar(mSonarMedianFilterSize, view.findViewById(R.id.sonar_median_filter_size_text))
        val medianFilterSize = sharedPref.getInt(
            Constants.SONAR_MEDIAN_FILTER_SIZE_KEY,
            Constants.SONAR_MEDIAN_FILTER_SIZE_DEFAULT
        )
        mSonarMedianFilterSize!!.progress = medianFilterSize
        val saveButton = view.findViewById<Button>(R.id.save_settings)
        saveButton.setOnClickListener { save() }
        return view
    }

    private fun save() {
        val editor = requireContext().getSharedPreferences(
            Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE
        ).edit()
        editor.putBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, mInverseServo!!.isChecked)
        editor.putInt(Constants.SONAR_MAX_DIST_KEY, mSonarMaxDist!!.progress)
        editor.putInt(Constants.SONAR_TOLERANCE_KEY, mSonarTolerance!!.progress)
        editor.putInt(Constants.SONAR_MEDIAN_FILTER_SIZE_KEY, mSonarMedianFilterSize!!.progress)
        editor.commit()
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        save()
    }
}
