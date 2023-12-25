package de.dizcza.fundu_moto_joystick.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.ListenableFuture
import com.marcinmoskala.arcseekbar.ArcSeekBar
import com.marcinmoskala.arcseekbar.ProgressListener
import de.dizcza.fundu_moto_joystick.HandsTracker
import de.dizcza.fundu_moto_joystick.LandmarksOverlayView
import de.dizcza.fundu_moto_joystick.R
import de.dizcza.fundu_moto_joystick.serial.SerialListener
import de.dizcza.fundu_moto_joystick.serial.SerialService
import de.dizcza.fundu_moto_joystick.serial.SerialService.SerialBinder
import de.dizcza.fundu_moto_joystick.serial.SerialSocket
import de.dizcza.fundu_moto_joystick.util.CommandParser
import de.dizcza.fundu_moto_joystick.util.Constants
import de.dizcza.fundu_moto_joystick.util.ToastRefrain
import de.dizcza.fundu_moto_joystick.util.Utils
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class JoystickFragment : Fragment(), ServiceConnection, SerialListener {
    private enum class Connected {
        False,
        Pending,
        True
    }

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var socket: SerialSocket? = null
    private var service: SerialService? = null
    private var initialStart = true
    private var connected = Connected.False
    private var mLogsFragment: LogsFragment? = null
    private var mServoSlider: ArcSeekBar? = null
    private var mSonarView: SonarView? = null
    private var mAutonomousBtn: ToggleButton? = null
    private var mPointerInitPos: PointF? = null
    private var mConnectDeviceMenuItem: MenuItem? = null
    private var mPointerCenter: PointF? = null
    private var mCommandParser: CommandParser? = null
    private var mLastSentCommand = ""

    private lateinit var previewView: PreviewView
    private lateinit var mediapipeExecutor: ExecutorService
    private lateinit var handsTracker: HandsTracker
    private lateinit var overlayView: LandmarksOverlayView
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var cameraProvider: ProcessCameraProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        deviceAddress = requireArguments().getString("device")
        deviceName = requireArguments().getString("deviceName")
        mLogsFragment = LogsFragment()
        mLogsFragment!!.setJoystickFragment(this)
    }

    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun startCameraSimple() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val camera = cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            preview
        )
    }


    override fun onDestroy() {
        if (connected != Connected.False) {
            turnOffAutomaticMode()
            disconnect()
        }
        requireContext().stopService(Intent(context, SerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (service != null) service!!.attach(this) else requireContext().startService(
            Intent(
                context,
                SerialService::class.java
            )
        ) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    override fun onStop() {
        if (service != null && !requireActivity().isChangingConfigurations) service!!.detach()
        super.onStop()
    }

    @Suppress("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        requireContext().bindService(
            Intent(context, SerialService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        try {
            requireContext().unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        mLastSentCommand = ""
        if (initialStart && service != null) {
            initialStart = false
            connect()
        }
        if (connected == Connected.True) {
            sendDeviceSettings()
            sendServoAngle(mServoSlider!!.progress)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialBinder).service
        if (initialStart && isResumed) {
            initialStart = false
            connect()
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    private fun sendServoAngle(progress: Int) {
        val progressNormalized = progress / mServoSlider!!.maxProgress.toFloat()
        var angle = (progressNormalized * 180 - 90).toInt()
        if (Utils.isInverseServoAngleNeeded(context)) {
            angle = -angle
        }
        val servoCommand = String.format(Locale.ENGLISH, "S%03d%s", angle, Constants.NEW_LINE)
        send(servoCommand.toByteArray())
    }

    /*
     * UI
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val joystickView = inflater.inflate(R.layout.joystick, container, false)
        previewView = joystickView.findViewById(R.id.view_finder)
        mSonarView = joystickView.findViewById(R.id.sonar_view)
        mSonarView!!.setOnClickListener(View.OnClickListener { v: View? -> mSonarView!!.clear() })
        mCommandParser = CommandParser(context, mSonarView)
        mServoSlider = joystickView.findViewById(R.id.servo_slider)
        val intArray = resources.getIntArray(R.array.progressGradientColors)
        mServoSlider!!.setProgressGradient(*intArray)
        mServoSlider!!.onProgressChangedListener =
            ProgressListener { progress -> sendServoAngle(progress) }
        val beepVolumeImg = joystickView.findViewById<ImageView>(R.id.beep_volume)
        beepVolumeImg.alpha = Constants.BEEP_VOLUME_ALPHA_OFF
        val beepBtn = joystickView.findViewById<Button>(R.id.beep_btn)
        beepBtn.setOnTouchListener { v: View?, event: MotionEvent ->
            var action = '\u0000'
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    action = '1' // activate buzzer
                    beepVolumeImg.alpha = Constants.BEEP_VOLUME_ALPHA_ON
                }

                MotionEvent.ACTION_UP -> {
                    action = '0' // deactivate buzzer
                    beepVolumeImg.alpha = Constants.BEEP_VOLUME_ALPHA_OFF
                }
            }
            if (action != '\u0000') {
                val hornCmd = String.format(Locale.ENGLISH, "B%c%s", action, Constants.NEW_LINE)
                send(hornCmd.toByteArray(), true)
                return@setOnTouchListener true
            }
            false
        }
        mAutonomousBtn = joystickView.findViewById(R.id.autonomous_btn)
        mAutonomousBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            val updatedState = if (mAutonomousBtn!!.isChecked()) 1 else 0
            val autonomousCmd =
                String.format(Locale.ENGLISH, "A%d%s", updatedState, Constants.NEW_LINE)
            val success = send(autonomousCmd.toByteArray())
            if (!success) {
                // discard checked update
                mAutonomousBtn!!.setChecked(!mAutonomousBtn!!.isChecked())
            }
        })
        val borderlineWidth =
            resources.getDimensionPixelSize(R.dimen.circle_background_borderline_width)
        val innerCircle = joystickView.findViewById<ImageView>(R.id.circle_direction_view)
        val outerCircle = joystickView.findViewById<ImageView>(R.id.circle_background_view)
        innerCircle.setOnTouchListener { view, motionEvent ->
            if (mPointerInitPos == null) {
                mPointerInitPos = PointF(view.x, view.y)
                mPointerCenter = PointF(
                    mPointerInitPos!!.x + view.width / 2f,
                    mPointerInitPos!!.y + view.height / 2f
                )
            }
            var moveVector: PointF? = null
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_MOVE -> {
                    val maxDisplacement =
                        (outerCircle.width - innerCircle.width) / 2f - borderlineWidth
                    // motionEvent.getX() returns X's position relative to the inner circle upper left corner
                    val newPos = PointF(
                        view.x + motionEvent.x,
                        view.y + motionEvent.y
                    )
                    var dx_pixels = newPos.x - mPointerCenter!!.x
                    var dy_pixels = newPos.y - mPointerCenter!!.y
                    val dist = Math.sqrt((dx_pixels * dx_pixels + dy_pixels * dy_pixels).toDouble())
                        .toFloat()
                    if (dist > maxDisplacement) {
                        val scale = maxDisplacement / dist
                        dx_pixels *= scale
                        dy_pixels *= scale
                        newPos.x = mPointerCenter!!.x + dx_pixels
                        newPos.y = mPointerCenter!!.y + dy_pixels
                    }
                    moveVector = PointF(
                        dx_pixels / maxDisplacement,
                        -dy_pixels / maxDisplacement
                    )
                    view.x = newPos.x - view.width / 2f
                    view.y = newPos.y - view.height / 2f
                }

                MotionEvent.ACTION_UP -> {
                    view.x = mPointerInitPos!!.x
                    view.y = mPointerInitPos!!.y
                    moveVector = PointF(0f, 0f)
                }
            }
            if (moveVector != null) {
                val x = moveVector.x
                val y = moveVector.y
                val angle = Math.atan2(y.toDouble(), x.toDouble()) * 180f / Math.PI
                val radiusNorm = Math.sqrt((x * x + y * y).toDouble())
                val motorCommand = String.format(
                    Locale.ENGLISH,
                    "M%04d,%.2f%s",
                    angle.toInt(),
                    radiusNorm,
                    Constants.NEW_LINE
                )
                send(motorCommand.toByteArray())
            }
            true
        }

        overlayView = joystickView.findViewById(R.id.overlay)

        mediapipeExecutor = Executors.newSingleThreadExecutor()
        mediapipeExecutor.execute {
            handsTracker = HandsTracker(context = requireContext(), handsTrackerListener = overlayView)
        }

        requestCameraPermissionAndStart()

        return joystickView
    }


    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(previewView.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(mediapipeExecutor) { image ->
                        handsTracker(imageProxy = image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            val camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }


    private fun bindCameraValerii() {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

        val imageAnalyzer = ImageAnalysis.Builder().setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            .apply { setAnalyzer(mediapipeExecutor) { image -> handsTracker(imageProxy = image) } }

        cameraProvider.unbindAll()

        val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }
        cameraProvider.bindToLifecycle(
            this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalyzer, preview
        )
    }

    private fun requestCameraPermissionAndStart() {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startCamera()
                }
            }

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } else {
            startCamera()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_joystick, menu)
        mConnectDeviceMenuItem = menu.findItem(R.id.connect)
        updateConnectionStatus()
    }

    private fun sendDeviceSettings() {
        val sharedPref =
            requireContext().getSharedPreferences(Constants.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val upperbound = resources.getInteger(R.integer.sonar_max_dist_lowerbound)
        val sonarMaxDist = sharedPref.getInt(Constants.SONAR_MAX_DIST_KEY, upperbound)
        val sonarTolerance =
            sharedPref.getInt(Constants.SONAR_TOLERANCE_KEY, Constants.SONAR_TOLERANCE_DEFAULT)
        val sonarMedianFilterSize = sharedPref.getInt(
            Constants.SONAR_MEDIAN_FILTER_SIZE_KEY,
            Constants.SONAR_MEDIAN_FILTER_SIZE_DEFAULT
        )
        val maxSonarDistCmd =
            String.format(Locale.ENGLISH, "D%03d%s", sonarMaxDist, Constants.NEW_LINE)
        val sonarToleranceCmd =
            String.format(Locale.ENGLISH, "T%01d%s", sonarTolerance, Constants.NEW_LINE)
        val sonarMedianFilterSizeCmd =
            String.format(Locale.ENGLISH, "F%01d%s", sonarMedianFilterSize, Constants.NEW_LINE)
        val cmd = maxSonarDistCmd + sonarToleranceCmd + sonarMedianFilterSizeCmd
        send(cmd.toByteArray())
    }

    private fun getResourceString(resId: Int): String {
        return resources.getString(resId) + '\n'
    }

    private fun updateConnectionStatus() {
        if (mConnectDeviceMenuItem == null) {
            return
        }
        when (connected) {
            Connected.False -> {
                mConnectDeviceMenuItem!!.setChecked(false)
                mConnectDeviceMenuItem!!.setIcon(R.drawable.ic_action_connect)
            }

            Connected.Pending, Connected.True -> {
                mConnectDeviceMenuItem!!.setChecked(true)
                mConnectDeviceMenuItem!!.setIcon(R.drawable.ic_action_disconnect)
            }
        }
    }

    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connected = Connected.Pending
            updateConnectionStatus()
            socket = SerialSocket()
            val message = String.format(
                Locale.ENGLISH, "%s to %s\n",
                stripResourceNewLine(R.string.connected), deviceName
            )
            service!!.connect(this, message)
            socket!!.connect(context, service, device)
            val messagePending = getResourceString(R.string.connecting)
            mLogsFragment!!.appendStatus(messagePending)
            Toast.makeText(context, messagePending, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun turnOffAutomaticMode() {
        // 'A0' switches the control to the manual mode
        val manualMode = String.format(Locale.ENGLISH, "A0%s", Constants.NEW_LINE)
        send(manualMode.toByteArray())
    }

    private fun disconnect() {
        mAutonomousBtn!!.isChecked = false
        connected = Connected.False
        service!!.disconnect()
        socket!!.disconnect()
        updateConnectionStatus()
        mSonarView!!.clear()
        val message = stripResourceNewLine(R.string.disconnected) + " from device\n"
        mLogsFragment!!.appendStatus(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun send(data: ByteArray): Boolean {
        return send(data, false)
    }

    private fun send(data: ByteArray, force: Boolean): Boolean {
        if (connected != Connected.True) {
            ToastRefrain.showText(context, "Serial device not connected", Toast.LENGTH_SHORT)
            return false
        }
        val command = String(data)
        if (!force && command == mLastSentCommand) {
            return false
        }
        var success: Boolean
        try {
            socket!!.write(data)
            mLogsFragment!!.appendSent(command)
            mLastSentCommand = command
            success = true
            Log.d(TAG, command)
        } catch (e: IOException) {
            onSerialIoError(e)
            success = false
        }
        return success
    }

    private fun receive(data: ByteArray) {
        mCommandParser!!.receive(data)
        mLogsFragment!!.appendReceived(String(data))
    }

    private fun stripResourceNewLine(resId: Int): String {
        val str = getResourceString(resId)
        return str.substring(0, str.length - 1)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.connect) {
            if (item.isChecked) {
                turnOffAutomaticMode()
                disconnect()
                mSonarView!!.clear()
            } else {
                connect()
            }
            return true
        } else if (item.itemId == R.id.show_logs) {
            requireFragmentManager().beginTransaction().replace(R.id.fragment, mLogsFragment!!)
                .addToBackStack(null).commit()
            return true
        } else if (item.itemId == R.id.settings_menu) {
            requireFragmentManager().beginTransaction().replace(R.id.fragment, SettingsFragment())
                .addToBackStack(null).commit()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        connected = Connected.True
        if (mServoSlider != null) {
            sendServoAngle(mServoSlider!!.progress)
        }
        updateConnectionStatus()
        val message = String.format(
            Locale.ENGLISH, "%s to %s\n",
            stripResourceNewLine(R.string.connected), deviceName
        )
        mLogsFragment!!.appendStatus(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSerialConnectError(e: Exception) {
        mLogsFragment!!.appendStatus("Connection failed: " + e.message + '\n')
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        receive(data)
    }

    override fun onSerialIoError(e: Exception) {
        mLogsFragment!!.appendStatus("Connection lost: " + e.message + '\n')
        disconnect()
    }

    companion object {
        private val TAG = JoystickFragment::class.java.name
    }
}
