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
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class JoystickFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private String newline = "\r\n";

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private PointF mCircleInitPos;
    private final Point mMoveVector = new Point(0, 0);
    private Timer mTimer;
    private static final long SEND_UPDATE_PERIOD = 100;  // ms
    private static final int VECTOR_AMPLITUDE = 100;  // can be any value; avoid transmitting floats
    private TimerTask mTimerSendTask;
    private Handler mHandler;

    private class TimerSendTask extends TimerTask {

        TimerSendTask() {
            super();
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
        }

        @Override
        public void run() {
            String message;
            synchronized (mMoveVector) {
                message = String.format("X%dY%d%s", mMoveVector.x, mMoveVector.y, newline);
            }
            send(message);
        }
    }

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
        mTimer = new Timer();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        mTimer.cancel();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        mTimerSendTask = new TimerSendTask();
        mTimer.scheduleAtFixedRate(mTimerSendTask, 1000, SEND_UPDATE_PERIOD);  // start after one second
        if(initialStart && service !=null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mTimerSendTask.cancel();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
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
        ImageView directionCircleView = joystickView.findViewById(R.id.circle_direction_view);
        ImageView bgCircleView = joystickView.findViewById(R.id.circle_background_view);
        directionCircleView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (mCircleInitPos == null) {
                    mCircleInitPos = new PointF(view.getX(), view.getY());
                }
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE: {
                        PointF newPos = new PointF(motionEvent.getRawX() - bgCircleView.getX(),
                                motionEvent.getRawY() - bgCircleView.getY());
                        float dx_pixels = newPos.x - mCircleInitPos.x;
                        float dy_pixels = newPos.y - mCircleInitPos.y;
                        final float dist = (float) Math.sqrt(dx_pixels * dx_pixels + dy_pixels * dy_pixels);
                        final float radius = bgCircleView.getWidth() / 2.f;
                        if (dist > radius) {
                            final float scale = radius / dist;
                            dx_pixels *= scale;
                            dy_pixels *= scale;
                            newPos.x = mCircleInitPos.x + dx_pixels;
                            newPos.y = mCircleInitPos.y + dy_pixels;
                        }
                        synchronized (mMoveVector) {
                            mMoveVector.x = (int) (VECTOR_AMPLITUDE * dx_pixels / radius);
                            mMoveVector.y = (int) (VECTOR_AMPLITUDE * dy_pixels / radius);
                        }
                        view.setX(newPos.x);
                        view.setY(newPos.y);
                    }
                    break;
                    case MotionEvent.ACTION_UP: {
                        view.setX(mCircleInitPos.x);
                        view.setY(mCircleInitPos.y);
                        synchronized (mMoveVector) {
                            mMoveVector.x = 0;
                            mMoveVector.y = 0;
                        }
                    }
                    break;
                }
                return true;
            }
        });
        return joystickView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            status("connecting...");
            connected = Connected.Pending;
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
        socket = null;
    }

    private boolean send(String str) {
        if(connected != Connected.True) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                }
            });
            return false;
        }
        boolean success;
        try {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("JoystickSend", str);
                }
            });
            byte[] data = (str + newline).getBytes();
            socket.write(data);
            success = true;
        } catch (IOException e) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onSerialIoError(e);
                }
            });
            success = false;
        }
        return success;
    }

    private void receive(byte[] data) {
    }

    private void status(String str) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.newline) {
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
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
