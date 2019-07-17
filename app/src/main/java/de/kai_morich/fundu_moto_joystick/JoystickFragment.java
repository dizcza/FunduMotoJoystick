package de.kai_morich.fundu_moto_joystick;

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
import android.os.Handler;
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
import android.widget.TextView;
import android.widget.ToggleButton;

import com.marcinmoskala.arcseekbar.ArcSeekBar;
import com.marcinmoskala.arcseekbar.ProgressListener;

import java.io.IOException;
import java.util.Locale;

public class JoystickFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private String newline = "\r\n";

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;
    private TextView mConnectionStatusText;
    private ToggleButton mConnectButton;

    private PointF mPointerInitPos;
    private PointF mPointerCenter;
    private static final String TAG = JoystickFragment.class.getName();
    private final Handler mHandler;
    private FunduLogs mFunduLogs;
    private ArcSeekBar mServoSlider;
    private SonarView mSonarView;
    private CommandParser mCommandParser;


    public JoystickFragment() {
        mHandler = new Handler();
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
        mFunduLogs = new FunduLogs(getContext());
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
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
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectionStatus();
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

    /*
     * UI
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View joystickView = inflater.inflate(R.layout.joystick, container, false);

        mSonarView = joystickView.findViewById(R.id.sonar_view);
        mCommandParser = new CommandParser(mSonarView);

        mServoSlider = joystickView.findViewById(R.id.servo_slider);
        int[] intArray = getResources().getIntArray(R.array.progressGradientColors);
        mServoSlider.setProgressGradient(intArray);
        mServoSlider.setOnProgressChangedListener(new ProgressListener() {
            @Override
            public void invoke(int progress) {
                float progressNormalized = progress / (float) mServoSlider.getMaxProgress();
                int angle = (int) (progressNormalized * 180 - 90);
                String servoCommand = String.format(Locale.ENGLISH, "S%03d%s", angle, newline);
                send(servoCommand.getBytes());
                Log.d(TAG, servoCommand);
            }
        });

        mConnectionStatusText = joystickView.findViewById(R.id.bluetooth_connection_status);
        mConnectButton = joystickView.findViewById(R.id.bluetooth_connection_toggle);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectButton.isChecked()) {
                    connect();
                } else {
                    disconnect();
                }
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
                    int angle = (int) Math.atan2(y, x);
                    double radiusNorm = Math.sqrt(x * x + y * y);
                    String motorCommand = String.format(Locale.ENGLISH, "M%03d,%.2f%s", angle, radiusNorm, newline);
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
                mConnectButton.setChecked(false);
                mConnectButton.setEnabled(true);
                mConnectionStatusText.setTextColor(getResources().getColor(R.color.failed));
                mConnectionStatusText.setText(R.string.disconnected);
                break;
            case Pending:
                mConnectButton.setChecked(true);
                mConnectButton.setEnabled(false);
                mConnectionStatusText.setTextColor(getResources().getColor(R.color.pending));
                mConnectionStatusText.setText(R.string.connecting);
                break;
            case True:
                mConnectButton.setChecked(true);
                mConnectButton.setEnabled(true);
                mConnectionStatusText.setTextColor(getResources().getColor(R.color.success));
                mConnectionStatusText.setText(R.string.connected);
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
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            mFunduLogs.appendLogs(getResourceString(R.string.connecting));
            connected = Connected.Pending;
            updateConnectionStatus();
            socket = new SerialSocket();
            service.connect(this, "Connected to " + deviceName);
            socket.connect(getContext(), service, device);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        mFunduLogs.appendLogs(getResourceString(R.string.disconnected));
        updateConnectionStatus();
    }

    private boolean send(byte[] data) {
        if (connected != Connected.True) {
            return false;
        }
        boolean success;
        try {
            socket.write(data);
            success = true;
        } catch (IOException e) {
            mHandler.post(() -> onSerialIoError(e));
            success = false;
        }
        return success;
    }

    private void receive(byte[] data) {
        mCommandParser.receive(data);
        mFunduLogs.appendLogs(new String(data));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int idSelected = item.getItemId();
        if (idSelected == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (idSelected == R.id.show_logs) {
            Fragment fragment = new LogsFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment).addToBackStack(null).commit();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        mFunduLogs.appendLogs(getResourceString(R.string.connected));
        connected = Connected.True;
        updateConnectionStatus();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mFunduLogs.appendLogs("Connection failed: " + e.getMessage() + '\n');
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        mFunduLogs.appendLogs("Connection lost: " + e.getMessage() + '\n');
        disconnect();
    }

}
