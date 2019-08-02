package de.dizcza.fundu_moto_joystick.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import de.dizcza.fundu_moto_joystick.OnBackPressed;
import de.dizcza.fundu_moto_joystick.R;
import de.dizcza.fundu_moto_joystick.util.Constants;


public class SettingsFragment extends Fragment implements OnBackPressed {

    private CheckBox mInverseServo;
    private SeekBar mSonarMaxDist;
    private SeekBar mSonarTolerance;
    private SeekBar mSonarMedianFilterSize;

    public SettingsFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    private void bindSeekBar(SeekBar seekBar, final TextView textView) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings, container, false);
        mInverseServo = view.findViewById(R.id.invert_servo);
        SharedPreferences sharedPref = getContext().getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE);
        boolean inverseAngle = sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false);
        mInverseServo.setChecked(inverseAngle);

        mSonarMaxDist = view.findViewById(R.id.max_sonar_dist);
        bindSeekBar(mSonarMaxDist, view.findViewById(R.id.max_sonar_dist_text));
        int maxDist = sharedPref.getInt(Constants.SONAR_MAX_DIST_KEY, Constants.SONAR_DIST_UPPERBOUND);
        mSonarMaxDist.setProgress(maxDist);

        mSonarTolerance = view.findViewById(R.id.sonar_tolerance);
        bindSeekBar(mSonarTolerance, view.findViewById(R.id.sonar_tolerance_text));
        int toleranceCm = sharedPref.getInt(Constants.SONAR_TOLERANCE_KEY, Constants.SONAR_TOLERANCE_DEFAULT);
        mSonarTolerance.setProgress(toleranceCm);

        mSonarMedianFilterSize = view.findViewById(R.id.sonar_median_filter_size);
        bindSeekBar(mSonarMedianFilterSize, view.findViewById(R.id.sonar_median_filter_size_text));
        int medianFilterSize = sharedPref.getInt(Constants.SONAR_MEDIAN_FILTER_SIZE_KEY, Constants.SONAR_MEDIAN_FILTER_SIZE_DEFAULT);
        mSonarMedianFilterSize.setProgress(medianFilterSize);

        Button saveButton = view.findViewById(R.id.save_settings);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        return view;
    }

    private int getProgressValid(SeekBar seekBar, int lowerbound) {
        int value = seekBar.getProgress();
        if (value < lowerbound) {
            seekBar.setProgress(lowerbound);
            value = lowerbound;
        }
        return value;
    }

    private void save() {
        SharedPreferences.Editor editor = getContext().getSharedPreferences(
                Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE).edit();
        int maxSonarDist = getProgressValid(mSonarMaxDist, Constants.SONAR_DIST_LOWERBOUND);
        int sonarTolerance = getProgressValid(mSonarTolerance, 1);
        int sonarMedianFilterSize = getProgressValid(mSonarMedianFilterSize, 1);

        editor.putBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, mInverseServo.isChecked());
        editor.putInt(Constants.SONAR_MAX_DIST_KEY, maxSonarDist);
        editor.putInt(Constants.SONAR_TOLERANCE_KEY, sonarTolerance);
        editor.putInt(Constants.SONAR_MEDIAN_FILTER_SIZE_KEY, sonarMedianFilterSize);
        editor.commit();

        Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        save();
    }
}
