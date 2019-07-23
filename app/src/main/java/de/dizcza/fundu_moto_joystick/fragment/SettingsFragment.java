package de.dizcza.fundu_moto_joystick.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Map;

import de.dizcza.fundu_moto_joystick.OnBackPressed;
import de.dizcza.fundu_moto_joystick.util.Constants;
import de.dizcza.fundu_moto_joystick.R;


public class SettingsFragment extends Fragment implements OnBackPressed {

    private CheckBox mInverseServo;
    private EditText mSonarMaxDist;
    private EditText mSonarTolerance;
    private EditText mSonarMedianFilterSize;
    private RadioGroup mRadioGroup;
    private final Map<Integer, String> mRadioGroupIdMap = new ArrayMap<>(2);

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
        boolean inverseAngle = sharedPref.getBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, false);
        mInverseServo.setChecked(inverseAngle);

        TextView sonarDistText = view.findViewById(R.id.sonar_dist_text);
        sonarDistText.setText(String.format(Locale.ENGLISH, "Limit sonar range dist in cm\n(between %s and %s):",
                Constants.SONAR_DIST_LOWERBOUND,
                Constants.SONAR_DIST_UPPERBOUND));

        mSonarMaxDist = view.findViewById(R.id.max_sonar_dist);
        int maxDist = sharedPref.getInt(Constants.SONAR_MAX_DIST_KEY, Constants.SONAR_DIST_UPPERBOUND);
        mSonarMaxDist.setText(String.valueOf(maxDist));

        mSonarTolerance = view.findViewById(R.id.sonar_tolerance);
        int toleranceCm = sharedPref.getInt(Constants.SONAR_TOLERANCE_KEY, Constants.SONAR_TOLERANCE_DEFAULT);
        mSonarTolerance.setText(String.valueOf(toleranceCm));

        mSonarMedianFilterSize = view.findViewById(R.id.sonar_median_filter_size);
        int medianFilterSize = sharedPref.getInt(Constants.SONAR_MEDIAN_FILTER_SIZE_KEY, Constants.SONAR_MEDIAN_FILTER_SIZE_DEFAULT);
        mSonarMedianFilterSize.setText(String.valueOf(medianFilterSize));

        mRadioGroupIdMap.clear();
        mRadioGroupIdMap.put(R.id.radio_newline_lf, Constants.NEW_LINE_MODE_LF);
        mRadioGroupIdMap.put(R.id.radio_newline_cr_lf, Constants.NEW_LINE_MODE_CR_LF);

        mRadioGroup = view.findViewById(R.id.radio_newline);
        String currNewline = sharedPref.getString(Constants.NEW_LINE_MODE_KEY, Constants.NEW_LINE_MODE_CR_LF);
        for (Map.Entry<Integer, String> entry : mRadioGroupIdMap.entrySet()) {
            if (currNewline.equals(entry.getValue())) {
                RadioButton radioButton = mRadioGroup.findViewById(entry.getKey());
                radioButton.setChecked(true);
                break;
            }
        }

        Button saveButton = view.findViewById(R.id.save_settings);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        return view;
    }

    private void save() {
        SharedPreferences.Editor editor = getContext().getSharedPreferences(
                Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE).edit();
        int maxSonarDist;
        try {
            maxSonarDist = Integer.parseInt(String.valueOf(mSonarMaxDist.getText()));
            if (maxSonarDist < Constants.SONAR_DIST_LOWERBOUND || maxSonarDist > Constants.SONAR_DIST_UPPERBOUND) {
                String msg = String.format(Locale.ENGLISH, "Entered value '%d' is outside of the range [%d, %d]",
                        maxSonarDist, Constants.SONAR_DIST_LOWERBOUND, Constants.SONAR_DIST_UPPERBOUND);
                throw new NumberFormatException(msg);
            }
        } catch (NumberFormatException e) {
            maxSonarDist = Constants.SONAR_DIST_UPPERBOUND;
            mSonarMaxDist.setText(String.valueOf(maxSonarDist));
            e.printStackTrace();
        }

        int sonarTolerance = Integer.parseInt(String.valueOf(mSonarTolerance.getText()));
        int sonarMedianFilterSize = Integer.parseInt(String.valueOf(mSonarMedianFilterSize.getText()));

        String newline = mRadioGroupIdMap.get(mRadioGroup.getCheckedRadioButtonId());

        editor.putBoolean(Constants.INVERSE_SERVO_ANGLE_KEY, mInverseServo.isChecked());
        editor.putInt(Constants.SONAR_MAX_DIST_KEY, maxSonarDist);
        editor.putInt(Constants.SONAR_TOLERANCE_KEY, sonarTolerance);
        editor.putInt(Constants.SONAR_MEDIAN_FILTER_SIZE_KEY, sonarMedianFilterSize);
        editor.putString(Constants.NEW_LINE_MODE_KEY, newline);
        editor.commit();

        Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        save();
    }
}
