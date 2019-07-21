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
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;

import de.dizcza.fundu_moto_joystick.util.Constants;
import de.dizcza.fundu_moto_joystick.R;


public class SettingsFragment extends Fragment {

    private CheckBox mInverseServo;
    private EditText mMaxSonarDist;
    private Button mSaveButton;

    public SettingsFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings, container, false);
        mInverseServo = view.findViewById(R.id.invert_servo);
        SharedPreferences sharedPref = getContext().getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE);
        boolean inverseAngle = sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE, false);
        mInverseServo.setChecked(inverseAngle);

        TextView sonarDistText = view.findViewById(R.id.sonar_dist_text);
        sonarDistText.setText(String.format(Locale.ENGLISH, "Max sonar dist in cm (between %s and %s):",
                Constants.SERVO_ANGLE_LOWERBOUND,
                Constants.SERVO_ANGLE_UPPERBOUND));

        mMaxSonarDist = view.findViewById(R.id.max_sonar_dist);
        mMaxSonarDist.setText(String.valueOf(Constants.SERVO_ANGLE_UPPERBOUND));
        mSaveButton = view.findViewById(R.id.save_settings);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = getContext().getSharedPreferences(
                        Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE).edit();
                editor.putBoolean(Constants.INVERSE_SERVO_ANGLE, mInverseServo.isChecked());
                editor.commit();
            }
        });
        return view;
    }


}
