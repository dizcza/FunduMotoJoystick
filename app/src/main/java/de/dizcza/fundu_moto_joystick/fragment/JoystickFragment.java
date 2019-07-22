package de.dizcza.fundu_moto_joystick.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.marcinmoskala.arcseekbar.ArcSeekBar;
import com.marcinmoskala.arcseekbar.ProgressListener;

import java.io.IOException;
import java.util.Locale;

import de.dizcza.fundu_moto_joystick.util.CommandParser;
import de.dizcza.fundu_moto_joystick.R;
import de.dizcza.fundu_moto_joystick.serial.SerialListener;
import de.dizcza.fundu_moto_joystick.serial.SerialService;
import de.dizcza.fundu_moto_joystick.serial.SerialSocket;
import de.dizcza.fundu_moto_joystick.util.ToastRefrain;
import de.dizcza.fundu_moto_joystick.util.Utils;

public class JoystickFragment extends Fragment implements ServiceConnection, SerialListener {

    private static final String TAG = JoystickFragment.class.getName();

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private String deviceName;
    private String newline = "\r\n";

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private LogsFragment mLogsFragment;
    private ArcSeekBar mServoSlider;
    private SonarView mSonarView;
    private MenuItem mConnectDeviceMenuItem;

    private PointF mPointerInitPos;
    private PointF mPointerCenter;
    private CommandParser mCommandParser;
    private String mLastSentCommand = "";


    public JoystickFragment() {
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        deviceName = getArguments().getString("deviceName");
        mLogsFragment = new LogsFragment();
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getContext().stopService(new Intent(getContext(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getContext().startService(new Intent(getContext(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getContext().bindService(new Intent(getContext(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getContext().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        mLastSentCommand = "";
        if (initialStart && service != null) {
            initialStart = false;
            connect();
        }
        if (connected.equals(Connected.True)) {
            sendServoAngle(mServoSlider.getProgress());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if (initialStart && isResumed()) {
            initialStart = false;
            connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    private void sendServoAngle(int progress) {
        float progressNormalized = progress / (float) mServoSlider.getMaxProgress();
        int angle = (int) (progressNormalized * 180 - 90);
        if (Utils.isInverseServoAngleNeeded(getContext())) {
            angle = -angle;
        }
        String servoCommand = String.format(Locale.ENGLISH, "S%03d%s", angle, newline);
        send(servoCommand.getBytes());
        Log.d(TAG, servoCommand);
    }

    /*
     * UI
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View joystickView = inflater.inflate(R.layout.joystick, container, false);

        mSonarView = joystickView.findViewById(R.id.sonar_view);
        mCommandParser = new CommandParser(getContext(), mSonarView);

        mServoSlider = joystickView.findViewById(R.id.servo_slider);
        int[] intArray = getResources().getIntArray(R.array.progressGradientColors);
        mServoSlider.setProgressGradient(intArray);
        mServoSlider.setOnProgressChangedListener(new ProgressListener() {
            @Override
            public void invoke(int progress) {
                sendServoAngle(progress);
            }
        });

        ImageView directionCircleView = joystickView.findViewById(R.id.circle_direction_view);
        final ImageView outerCircle = joystickView.findViewById(R.id.circle_background_view);
        directionCircleView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (mPointerInitPos == null) {
                    mPointerInitPos = new PointF(view.getX(), view.getY());
                    mPointerCenter = new PointF(mPointerInitPos.x + view.getWidth() / 2.f,
                            mPointerInitPos.y + view.getHeight() / 2.f);
                }
                PointF moveVector = null;
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        mSonarView.clear();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        final float outerCircleRadius = outerCircle.getWidth() / 2.f;
                        // motionEvent.getX() returns X's position relative to inner circle upper left corner
                        PointF newPos = new PointF(view.getX() + motionEvent.getX(),
                                view.getY() + motionEvent.getY());
                        float dx_pixels = newPos.x - mPointerCenter.x;
                        float dy_pixels = newPos.y - mPointerCenter.y;
                        final float dist = (float) Math.sqrt(dx_pixels * dx_pixels + dy_pixels * dy_pixels);
                        if (dist > outerCircleRadius) {
                            final float scale = outerCircleRadius / dist;
                            dx_pixels *= scale;
                            dy_pixels *= scale;
                            newPos.x = mPointerCenter.x + dx_pixels;
                            newPos.y = mPointerCenter.y + dy_pixels;
                        }
                        moveVector = new PointF(dx_pixels / outerCircleRadius,
                                -dy_pixels / outerCircleRadius);
                        view.setX(newPos.x - view.getWidth() / 2.f);
                        view.setY(newPos.y - view.getHeight() / 2.f);
                        break;

                    case MotionEvent.ACTION_UP:
                        view.setX(mPointerInitPos.x);
                        view.setY(mPointerInitPos.y);
                        moveVector = new PointF(0, 0);
                        break;
                }
                if (moveVector != null) {
                    float x = moveVector.x;
                    float y = moveVector.y;
                    double angle = Math.atan2(y, x);
                    double radiusNorm = Math.sqrt(x * x + y * y);
                    String motorCommand = String.format(Locale.ENGLISH, "M%04d,%.2f%s", (int) angle, radiusNorm, newline);
                    send(motorCommand.getBytes());
                    Log.d(TAG, motorCommand);
                }
                return true;
            }
        });
        return joystickView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_joystick, menu);
    }

    private String getResourceString(int resId) {
        return getResources().getString(resId) + '\n';
    }

    private void updateConnectionStatus() {
        switch (connected) {
            case False:
                mConnectDeviceMenuItem.setChecked(false);
                mConnectDeviceMenuItem.setIcon(R.drawable.ic_action_connect);
                break;
            case Pending:
            case True:
                mConnectDeviceMenuItem.setChecked(true);
                mConnectDeviceMenuItem.setIcon(R.drawable.ic_action_disconnect);
                break;
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connected = Connected.Pending;
            updateConnectionStatus();
            socket = new SerialSocket();
            String message = String.format(Locale.ENGLISH, "%s to %s\n",
                    stripResourceNewLine(R.string.connected), deviceName);
            service.connect(this, message);
            socket.connect(getContext(), service, device);
            String messagePending = getResourceString(R.string.connecting);
            mLogsFragment.appendStatus(messagePending);
            Toast.makeText(getContext(), messagePending, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        updateConnectionStatus();
        String message = stripResourceNewLine(R.string.disconnected) + " from device\n";
        mLogsFragment.appendStatus(message);
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean send(byte[] data) {
        if (connected != Connected.True) {
            ToastRefrain.showText(getContext(), "Serial device not connected", Toast.LENGTH_SHORT);
            return false;
        }
        String command = new String(data);
        if (command.equals(mLastSentCommand)) {
            return false;
        }
        boolean success;
        try {
            socket.write(data);
            mLogsFragment.appendSent(command);
            mLastSentCommand = command;
            success = true;
        } catch (IOException e) {
            onSerialIoError(e);
            success = false;
        }
        return success;
    }

    private void receive(byte[] data) {
        mCommandParser.receive(data);
        mLogsFragment.appendReceived(new String(data));
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mConnectDeviceMenuItem = menu.findItem(R.id.connect);
        updateConnectionStatus();
    }

    private String stripResourceNewLine(int resId) {
        String str = getResourceString(resId);
        return str.substring(0, str.length() - 1);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                if (item.isChecked()) {
                    disconnect();
                    mSonarView.clear();
                } else {
                    connect();
                }
                return true;
            case R.id.newline:
                String[] newlineNames = getResources().getStringArray(R.array.newline_names);
                String[] newlineValues = getResources().getStringArray(R.array.newline_values);
                int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Newline");
                builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                    newline = newlineValues[item1];
                    dialog.dismiss();
                });
                builder.create().show();
                return true;
            case R.id.show_logs:
                getFragmentManager().beginTransaction().replace(R.id.fragment, mLogsFragment).addToBackStack(null).commit();
                return true;
            case R.id.settings_menu:
                getFragmentManager().beginTransaction().replace(R.id.fragment, new SettingsFragment()).addToBackStack(null).commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        connected = Connected.True;
        if (mServoSlider != null) {
            sendServoAngle(mServoSlider.getProgress());
        }
        updateConnectionStatus();
        String message = String.format(Locale.ENGLISH, "%s to %s\n",
                stripResourceNewLine(R.string.connected), deviceName);
        mLogsFragment.appendStatus(message);
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mLogsFragment.appendStatus("Connection failed: " + e.getMessage() + '\n');
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        mLogsFragment.appendStatus("Connection lost: " + e.getMessage() + '\n');
        disconnect();
    }

}
