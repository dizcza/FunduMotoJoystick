package de.dizcza.fundu_moto_joystick.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
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
import kotlin.math.atan2
import kotlin.math.sqrt


class JoystickFragment : Fragment(), ServiceConnection, SerialListener {
    private enum class Connected {
        False,
        Pending,
        True
    }

    private lateinit var deviceAddress: String
    private lateinit var deviceName: String
    private var socket: SerialSocket? = null
    private var service: SerialService? = null
    private var initialStart = true
    private var connected = Connected.False
    private lateinit var mLogsFragment: LogsFragment
    private var mServoSlider: ArcSeekBar? = null
    private lateinit var mSonarView: SonarView
    private lateinit var innerCircle: ImageView
    private lateinit var outerCircle: ImageView
    private lateinit var mAutonomousBtn: ToggleButton
    private var mConnectDeviceMenuItem: MenuItem? = null
    private var mCommandParser: CommandParser? = null
    private var mLastSentCommand = ""
    private var ringBorderlineWidth = 0

    private lateinit var previewView: PreviewView
    private lateinit var mediapipeExecutor: ExecutorService
    private lateinit var handsTracker: HandsTracker
    private lateinit var overlayView: LandmarksOverlayView
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var steeringWheelControl: SteeringWheelControl

    inner class SteeringWheelControl : View.OnTouchListener {

        fun sendMoveCommand(moveVector: PointF?) {
            if (moveVector != null && connected == Connected.True) {
                val x = moveVector.x
                val y = moveVector.y
                val angle = Math.toDegrees(atan2(y, x).toDouble())
                val radiusNorm = sqrt(x * x + y * y)
                val motorCommand = String.format(
                    "M%04d,%.2f%s",
                    angle.toInt(),
                    radiusNorm,
                    Constants.NEW_LINE
                )
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // on UI thread
                    send(motorCommand.toByteArray())
                } else {
                    Handler(Looper.getMainLooper()).post {
                        send(motorCommand.toByteArray())
                    }
                }
            }
        }

        fun updateTouchPosAndMoveVec(displacementX: Float, displacementY: Float) : Pair<PointF, PointF> {
            val pointerCenter = PointF(outerCircle.width / 2f, outerCircle.height / 2f)
            val maxDisplacement = (outerCircle.width - innerCircle.width) / 2f - ringBorderlineWidth
            val dist = sqrt(displacementX * displacementX + displacementY * displacementY)
            var displacementXBounded = displacementX
            var displacementYBounded = displacementY
            if (dist > maxDisplacement) {
                val scale = maxDisplacement / dist
                displacementXBounded *= scale
                displacementYBounded *= scale
            }
            val newPos = PointF(
                pointerCenter.x + displacementXBounded,
                pointerCenter.y + displacementYBounded
            )
            val moveVector = PointF(
                displacementX / maxDisplacement,
                -displacementY / maxDisplacement
            )
            return Pair(newPos, moveVector)
        }

        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            view.performClick()  // not really needed
            val pointerInitPos = PointF(
                (outerCircle.width - innerCircle.width) / 2f,
                (outerCircle.height - innerCircle.height) / 2f
            )
            val pointerCenter = PointF(outerCircle.width / 2f, outerCircle.height / 2f)
            var moveVector: PointF? = null
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_MOVE -> {
                    // motionEvent.x is relative to the inner circle upper left corner
                    val displacementX = view.x + motionEvent.x - pointerCenter.x
                    val displacementY = view.y + motionEvent.y - pointerCenter.y
                    val (newPos, moveVectorRes) = updateTouchPosAndMoveVec(displacementX, displacementY)
                    moveVector = moveVectorRes
                    view.x = newPos.x - view.width / 2f
                    view.y = newPos.y - view.height / 2f
                }

                MotionEvent.ACTION_UP -> {
                    view.x = pointerInitPos.x
                    view.y = pointerInitPos.y
                    moveVector = PointF(0f, 0f)
                }
            }
            sendMoveCommand(moveVector)
            return true
        }

    }

    inner class AirTouchWheelControl : HandsTracker.HandsTrackerListener {

        private var handDetected = false
        private val moveScale = 2f

        private fun airtouchSendMotionEvent(palmCenterNorm: PointF) {
            val displacementX = moveScale * (palmCenterNorm.x - 0.5f) * outerCircle.width / 2f
            val displacementY = moveScale * (palmCenterNorm.y - 0.5f) * outerCircle.height / 2f
            val (newPos, moveVector) = steeringWheelControl.updateTouchPosAndMoveVec(displacementX, displacementY)
            steeringWheelControl.sendMoveCommand(moveVector)
            Handler(Looper.getMainLooper()).post {
                innerCircle.x = newPos.x - innerCircle.width / 2f
                innerCircle.y = newPos.y - innerCircle.height / 2f
            }
            handDetected = true
        }

        private fun airtouchResetSteeringWheel() {
            if (!handDetected) {
                return
            }
            steeringWheelControl.sendMoveCommand(moveVector = PointF(0f, 0f))
            Handler(Looper.getMainLooper()).post {
                val pointerInitPos = PointF(
                    (outerCircle.width - innerCircle.width) / 2f,
                    (outerCircle.height - innerCircle.height) / 2f
                )
                innerCircle.x = pointerInitPos.x
                innerCircle.y = pointerInitPos.y
            }
            handDetected = false
        }

        override fun onHandsDetected(results: HandLandmarkerResult, imageHeight: Int, imageWidth: Int) {
            if (results.landmarks().isEmpty()) {
                airtouchResetSteeringWheel()
                return
            }
            val landmarks = results.landmarks().first()
            if (landmarks == null) {
                airtouchResetSteeringWheel()
                return
            }
            airtouchSendMotionEvent(getPalmCenter(landmarks))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        deviceAddress = requireArguments().getString("device").toString()
        deviceName = requireArguments().getString("deviceName").toString()
        mLogsFragment = LogsFragment()
        mLogsFragment.setJoystickFragment(this)
        ringBorderlineWidth = resources.getDimensionPixelSize(R.dimen.circle_background_borderline_width)
        steeringWheelControl = SteeringWheelControl()
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
        mSonarView.setOnClickListener(View.OnClickListener { v: View? -> mSonarView.clear() })
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
        mAutonomousBtn.setOnClickListener(View.OnClickListener { v: View? ->
            val updatedState = if (mAutonomousBtn.isChecked) 1 else 0
            val autonomousCmd =
                String.format(Locale.ENGLISH, "A%d%s", updatedState, Constants.NEW_LINE)
            val success = send(autonomousCmd.toByteArray())
            if (!success) {
                // discard checked update
                mAutonomousBtn.isChecked = !mAutonomousBtn.isChecked
            }
        })
        innerCircle = joystickView.findViewById(R.id.circle_direction_view)
        outerCircle = joystickView.findViewById(R.id.circle_background_view)

        innerCircle.setOnTouchListener(steeringWheelControl)

        overlayView = joystickView.findViewById(R.id.overlay)

        mediapipeExecutor = Executors.newSingleThreadExecutor()
        mediapipeExecutor.execute {
            handsTracker = HandsTracker(context = requireContext())
            handsTracker.addHandsTrackerListener(overlayView)
            handsTracker.addHandsTrackerListener(AirTouchWheelControl())
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
            mLogsFragment.appendStatus(messagePending)
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
        mAutonomousBtn.isChecked = false
        connected = Connected.False
        service!!.disconnect()
        socket!!.disconnect()
        updateConnectionStatus()
        mSonarView.clear()
        val message = stripResourceNewLine(R.string.disconnected) + " from device\n"
        mLogsFragment.appendStatus(message)
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
            mLogsFragment.appendSent(command)
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
        mLogsFragment.appendReceived(String(data))
    }

    private fun stripResourceNewLine(resId: Int): String {
        val str = getResourceString(resId)
        return str.substring(0, str.length - 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.connect -> {
                if (item.isChecked) {
                    turnOffAutomaticMode()
                    disconnect()
                    mSonarView.clear()
                } else {
                    connect()
                }
                return true
            }
            R.id.show_logs -> {
                requireFragmentManager().beginTransaction().replace(R.id.fragment, mLogsFragment)
                    .addToBackStack(null).commit()
                return true
            }
            R.id.settings_menu -> {
                requireFragmentManager().beginTransaction().replace(R.id.fragment, SettingsFragment())
                    .addToBackStack(null).commit()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
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
        mLogsFragment.appendStatus(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSerialConnectError(e: Exception) {
        mLogsFragment.appendStatus("Connection failed: " + e.message + '\n')
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        receive(data)
    }

    override fun onSerialIoError(e: Exception) {
        mLogsFragment.appendStatus("Connection lost: " + e.message + '\n')
        disconnect()
    }

    companion object {
        private val TAG = JoystickFragment::class.java.name
    }

    private fun getPalmCenter(landmarks: List<NormalizedLandmark>): PointF {
        var xCenter = 0f
        var yCenter = 0f

        val palmKeypoints = HashSet<Int>()

        HandLandmarker.HAND_PALM_CONNECTIONS.forEach { bone ->
            val startLandmarkIndex = bone.start()
            val endLandmarkIndex = bone.end()
            palmKeypoints.add(startLandmarkIndex)
            palmKeypoints.add(endLandmarkIndex)
        }

        for (palmKeypoint in palmKeypoints) {
            xCenter += landmarks[palmKeypoint].x()
            yCenter += landmarks[palmKeypoint].y()
        }

        xCenter /= palmKeypoints.size
        yCenter /= palmKeypoints.size

        return PointF(xCenter, yCenter)
    }

}
