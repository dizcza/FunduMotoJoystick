package de.dizcza.fundu_moto_joystick;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;

public class LogsFragment extends Fragment {

    private final LogsView mStatusView = new LogsView();
    private final LogsView mSentView = new LogsView();
    private final LogsView mReceivedView = new LogsView();

    private class LogsView {
        TextView view;
        final StringBuilder logs = new StringBuilder();

        private void setView(TextView view) {
            view.setMovementMethod(ScrollingMovementMethod.getInstance());
            this.view = view;
        }
    }

    public LogsFragment() {
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
        return view;
    }

    private Iterable<LogsView> getViews() {
        return Arrays.asList(mStatusView, mSentView, mReceivedView);
    }

    @Override
    public void onResume() {
        super.onResume();
        for (LogsView logsView : getViews()) {
            logsView.view.setText(logsView.logs.toString());
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
