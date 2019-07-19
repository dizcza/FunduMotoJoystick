package de.dizcza.fundu_moto_joystick;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LogsFragment extends Fragment {

    private TextView mLogsTextView;
    private final StringBuilder mFunduLogs = new StringBuilder();

    public static enum LogEvent {
        Status("[Status] "),
        Error("[Error] "),
        Transmitted("[TX] ");
        // Don't create a tag for RX because of the streaming nature of RX data.

        final String prefix;

        LogEvent(String tag) {
            this.prefix = tag;
        }
    }

    ;

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
        mLogsTextView = view.findViewById(R.id.logs_view);
        mLogsTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mLogsTextView.setTextColor(getResources().getColor(R.color.received));  // set as default color to reduce number of spans
        return view;
    }

    private SpannableStringBuilder getSpan(String str, int colorId) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str);
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(colorId)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spn;
    }

    private void showLogs() {
        String logs = mFunduLogs.toString();
        for (String line : logs.split("\n")) {
            line = line + '\n';
            if (line.startsWith(LogEvent.Status.prefix)) {
                mLogsTextView.append(getSpan(line, R.color.pending));
            } else if (line.startsWith(LogEvent.Error.prefix)) {
                mLogsTextView.append(getSpan(line, R.color.failed));
            } else if (line.startsWith(LogEvent.Transmitted.prefix)) {
                mLogsTextView.append(getSpan(line, R.color.transmitted));
            } else {
                mLogsTextView.append(line);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        showLogs();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logs, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int idSelected = item.getItemId();
        if (idSelected == R.id.clear) {
            mLogsTextView.setText("");
            mFunduLogs.setLength(0);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void appendText(String text) {
        mFunduLogs.append(text);
    }

    public void appendText(String text, LogEvent event) {
        text = event.prefix + text;
        mFunduLogs.append(text);
    }

}
