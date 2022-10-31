package de.dizcza.fundu_moto_joystick.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.dizcza.fundu_moto_joystick.R;
import de.dizcza.fundu_moto_joystick.util.Constants;

public class LogsFragment extends Fragment {

    private final LogsView mStatusView = new LogsView();
    private final LogsView mSentView = new LogsView();
    private final LogsView mReceivedView = new LogsView();
    private JoystickFragment mJoystickFragment;

    private static class LogsView {
        TextView view;
        final StringBuilder logs = new StringBuilder();

        private void setView(TextView view) {
            this.view = view;
        }
    }

    public LogsFragment() {
    }

    public void setJoystickFragment(JoystickFragment joystickFragment) {
        mJoystickFragment = joystickFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.logs_fragment, container, false);
        mStatusView.setView(view.findViewById(R.id.logs_status));
        mSentView.setView(view.findViewById(R.id.logs_sent));
        mReceivedView.setView(view.findViewById(R.id.logs_received));
        final EditText commandView = view.findViewById(R.id.send_text);
        final ImageButton sendCmdButton = view.findViewById(R.id.send_btn);
        sendCmdButton.setOnClickListener(v -> {
            final String command = commandView.getText().toString();
            if (mJoystickFragment.send(command.getBytes())) {
                mSentView.view.append(command + Constants.NEW_LINE);
            }
        });
        return view;
    }

    private Iterable<LogsView> getViews() {
        return Arrays.asList(mStatusView, mSentView, mReceivedView);
    }

    @Override
    public void onResume() {
        super.onResume();
        for (LogsView logsView : getViews()) {
            String text = logsView.logs.toString();
            List<String> textLines = Arrays.asList(text.split(Constants.NEW_LINE));
            Collections.reverse(textLines);
            String textReversed = text.join(Constants.NEW_LINE, textLines);
            logsView.view.setText(textReversed);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logs, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int idSelected = item.getItemId();
        if (idSelected == R.id.clear) {
            for (LogsView logsView : getViews()) {
                logsView.view.setText("");
                logsView.logs.setLength(0);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void appendStatus(String text) {
        mStatusView.logs.append(text);
    }


    public void appendSent(String text) {
        mSentView.logs.append(text);
    }


    public void appendReceived(String text) {
        mReceivedView.logs.append(text);
    }

}
