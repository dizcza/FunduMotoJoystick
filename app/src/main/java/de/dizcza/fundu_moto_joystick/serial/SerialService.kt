package de.dizcza.fundu_moto_joystick.serial

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import de.dizcza.fundu_moto_joystick.R
import de.dizcza.fundu_moto_joystick.util.Constants
import java.util.LinkedList
import java.util.Queue

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
class SerialService : Service(), SerialListener {
    inner class SerialBinder : Binder() {
        val service: SerialService
            get() = this@SerialService
    }

    private enum class QueueType {
        Connect,
        ConnectError,
        Read,
        IoError
    }

    private inner class QueueItem (
        var type: QueueType,
        var data: ByteArray?,
        var exception: Exception?
    )

    private val mainLooper: Handler = Handler(Looper.getMainLooper())
    private val binder: IBinder = SerialBinder()
    private val queue: Queue<QueueItem> = LinkedList()
    private var listener: SerialListener? = null
    private var connected = false
    private var notificationMsg: String? = null

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Api
     */
    fun connect(listener: SerialListener?, notificationMsg: String?) {
        this.listener = listener
        connected = true
        this.notificationMsg = notificationMsg
    }

    fun disconnect() {
        cancelNotification()
        listener = null
        connected = false
        notificationMsg = null
    }

    fun attach(listener: SerialListener) {
        require(Looper.getMainLooper().thread === Thread.currentThread()) { "not in main thread" }
        cancelNotification()
        if (connected) {
            synchronized(this) { this.listener = listener }
        }
        for (item in queue) {
            when (item.type) {
                QueueType.Connect -> listener.onSerialConnect()
                QueueType.ConnectError -> listener.onSerialConnectError(item.exception!!)
                QueueType.Read -> listener.onSerialRead(item.data!!)
                QueueType.IoError -> listener.onSerialIoError(item.exception!!)
            }
        }
        queue.clear()
    }

    fun detach() {
        if (connected) createNotification()
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // detach() and mainLooper.post run in the main thread, so all items are caught
        synchronized(this) {
            listener = null
        }
    }

    private fun createNotification() {
        val nc = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL,
            "Background service",
            NotificationManager.IMPORTANCE_LOW
        )
        nc.setShowBadge(false)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(nc)
        val disconnectIntent = Intent()
            .setAction(Constants.INTENT_ACTION_DISCONNECT)
        val restartIntent = Intent()
            .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val disconnectPendingIntent =
            PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val restartPendingIntent =
            PendingIntent.getActivity(this, 1, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(resources.getColor(R.color.colorPrimary))
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(notificationMsg)
            .setContentIntent(restartPendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_clear_white_24dp,
                    "Disconnect",
                    disconnectPendingIntent
                )
            )
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        val notification = builder.build()
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)
    }

    private fun cancelNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * SerialListener
     */
    override fun onSerialConnect() {
        if (connected) {
            synchronized(this) {
                mainLooper.post {
                    if (listener != null) {
                        listener!!.onSerialConnect()
                    } else {
                        queue.add(QueueItem(QueueType.Connect, null, null))
                    }
                }
            }
        }
    }

    override fun onSerialConnectError(e: Exception) {
        if (connected) {
            synchronized(this) {
                mainLooper.post {
                    if (listener != null) {
                        listener!!.onSerialConnectError(e)
                    } else {
                        queue.add(QueueItem(QueueType.ConnectError, null, e))
                        disconnect()
                    }
                }
            }
        }
    }

    override fun onSerialRead(data: ByteArray) {
        if (connected) {
            synchronized(this) {
                mainLooper.post {
                    if (listener != null) {
                        listener!!.onSerialRead(data)
                    } else {
                        queue.add(QueueItem(QueueType.Read, data, null))
                    }
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception) {
        if (connected) {
            synchronized(this) {
                mainLooper.post {
                    if (listener != null) {
                        listener!!.onSerialIoError(e)
                    } else {
                        queue.add(QueueItem(QueueType.IoError, null, e))
                        disconnect()
                    }
                }
            }
        }
    }
}
