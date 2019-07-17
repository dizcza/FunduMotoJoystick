package de.kai_morich.fundu_moto_joystick;

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

public class LogsFragment extends Fragment {

    private TextView mLogsTextView;
    private final StringBuilder mFunduLogs = new StringBuilder();

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
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mLogsTextView.setText(mFunduLogs.toString());
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

}
