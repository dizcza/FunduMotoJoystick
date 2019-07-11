package de.kai_morich.fundu_moto_joystick;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FunduLogs {

    private static final String LOGS_FILENAME = "FunduMoto_Logs.txt";
    private static final int SCROLL_BACK = 500;
    private final File mLogsFile;

    public FunduLogs(Context context) {
        File privateDir = context.getExternalFilesDir(null);
        mLogsFile = new File(privateDir, LOGS_FILENAME);
    }

    public void appendLogs(String message) {
        writeLogs(message, true);
    }

    public void writeLogs(String message, boolean append) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("[mm:ss.SSSS] ", Locale.US);
        String timestampTag = sdf.format(now.getTime());
        message = timestampTag + message;
        try {
            FileOutputStream fileOutput = new FileOutputStream(mLogsFile, append);
            fileOutput.write(message.getBytes());
            fileOutput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    Reads `SCROLL_BACK` last lines from the `LOGS_FILENAME` file.
    If the file contains more than `SCROLL_BACK` lines, it overrides the file with the last
    `SCROLL_BACK` lines.
     */
    public String readLogsInvasive() {
        List<Integer> newLinePos = new ArrayList<>(SCROLL_BACK);
        StringBuilder textBuilder = new StringBuilder();
        int cumsum = 0;
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(mLogsFile));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                textBuilder.append(line);
                textBuilder.append('\n');
                cumsum += line.length();
                newLinePos.add(cumsum);
            }
            bufferedReader.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        int startPos = 0;
        if (newLinePos.size() > SCROLL_BACK) {
            startPos = newLinePos.get(newLinePos.size() - SCROLL_BACK);
        }
        String logs = textBuilder.substring(startPos);
        if (startPos > 0) {
            writeLogs(logs, false);
        }
        return logs;
    }

    public void delete() {
        mLogsFile.delete();
    }

}
