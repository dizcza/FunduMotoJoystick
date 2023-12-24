package de.dizcza.fundu_moto_joystick.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.dizcza.fundu_moto_joystick.R
import de.dizcza.fundu_moto_joystick.util.Constants
import java.util.Arrays
import java.util.Collections

class LogsFragment : Fragment() {
    private val mStatusView = LogsView()
    private val mSentView = LogsView()
    private val mReceivedView = LogsView()
    private var mJoystickFragment: JoystickFragment? = null

    private class LogsView {
        var view: TextView? = null
        val logs = StringBuilder()
    }

    fun setJoystickFragment(joystickFragment: JoystickFragment?) {
        mJoystickFragment = joystickFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.logs_fragment, container, false)
        mStatusView.view = view.findViewById(R.id.logs_status)
        mSentView.view = view.findViewById(R.id.logs_sent)
        mReceivedView.view = view.findViewById(R.id.logs_received)
        val commandView = view.findViewById<EditText>(R.id.send_text)
        val sendCmdButton = view.findViewById<ImageButton>(R.id.send_btn)
        sendCmdButton.setOnClickListener { v: View? ->
            val command = commandView.text.toString()
            if (mJoystickFragment!!.send(command.toByteArray())) {
                mSentView.view!!.append(command + Constants.NEW_LINE)
            }
        }
        return view
    }

    private val views: Iterable<LogsView>
        private get() = Arrays.asList(mStatusView, mSentView, mReceivedView)

    override fun onResume() {
        super.onResume()
        for (logsView in views) {
            val text = logsView.logs.toString()
            val textLines = Arrays.asList(
                *text.split(Constants.NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            Collections.reverse(textLines)
            val textReversed = java.lang.String.join(Constants.NEW_LINE, textLines)
            logsView.view!!.text = textReversed
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_logs, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val idSelected = item.itemId
        return if (idSelected == R.id.clear) {
            for (logsView in views) {
                logsView.view!!.text = ""
                logsView.logs.setLength(0)
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    fun appendStatus(text: String?) {
        mStatusView.logs.append(text)
    }

    fun appendSent(text: String?) {
        mSentView.logs.append(text)
    }

    fun appendReceived(text: String?) {
        mReceivedView.logs.append(text)
    }
}
