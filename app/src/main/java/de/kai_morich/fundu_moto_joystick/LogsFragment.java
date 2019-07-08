package de.kai_morich.fundu_moto_joystick;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class LogsFragment extends Fragment {

    public static final String LOGS_FILENAME = "FunduMoto_Logs.txt";

    private TextView mLogsTextView;
    private File mLogsFile;

    public LogsFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        File privateDir = getContext().getExternalFilesDir(null);
        mLogsFile = new File(privateDir, LogsFragment.LOGS_FILENAME);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.logs_fragment, container, false);
        mLogsTextView = view.findViewById(R.id.logs_view);
        loadLogs();
        return view;
    }

    private void loadLogs() {
        StringBuilder textBuilder = new StringBuilder();
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(mLogsFile));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                textBuilder.append(line);
                textBuilder.append('\n');
            }
            bufferedReader.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        mLogsTextView.setText(textBuilder.toString());
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
            mLogsFile.delete();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

}
