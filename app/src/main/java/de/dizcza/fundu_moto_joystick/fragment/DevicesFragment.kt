package de.dizcza.fundu_moto_joystick.fragment

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import de.dizcza.fundu_moto_joystick.R
import java.util.Collections


internal class BluetoothDeviceAdapter(
    private val mActivity: Activity,
    resource: Int,
    private val listItems: List<BluetoothDevice>,
) :
    ArrayAdapter<BluetoothDevice>(mActivity, resource, listItems) {
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val device = listItems[position]
        val viewRes = view ?: mActivity.layoutInflater.inflate(R.layout.device_list_item, parent, false)
        val text1 = viewRes.findViewById<TextView>(R.id.text1)
        val text2 = viewRes.findViewById<TextView>(R.id.text2)
        text1.text = device.name
        text2.text = device.address
        return viewRes
    }
}


class DevicesFragment : ListFragment() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val listItems: ArrayList<BluetoothDevice> = ArrayList()
    private lateinit var listAdapter: BluetoothDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        listAdapter = BluetoothDeviceAdapter(requireActivity(), 0, listItems)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(null)
        val header = requireActivity().layoutInflater.inflate(R.layout.device_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("initializing...")
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices, menu)
        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) menu.findItem(
            R.id.bt_settings
        ).setEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null || !requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) setEmptyText(
            "<bluetooth not supported>"
        ) else if (!bluetoothAdapter.isEnabled) setEmptyText("<bluetooth is disabled>") else setEmptyText(
            "<no bluetooth devices found>"
        )
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.bt_settings) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            for (device in bluetoothAdapter.bondedDevices) if (device.type != BluetoothDevice.DEVICE_TYPE_LE) listItems.add(
                device
            )
        }
        Collections.sort(listItems) { a: BluetoothDevice, b: BluetoothDevice -> compareTo(a, b) }
        listAdapter.notifyDataSetChanged()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = listItems[position - 1]
        val args = Bundle()
        val deviceName = if (device.name != null) device.name else device.address
        args.putString("device", device.address)
        args.putString("deviceName", deviceName)
        val fragment: Fragment = JoystickFragment()
        fragment.arguments = args
        requireFragmentManager().beginTransaction().replace(R.id.fragment, fragment).addToBackStack(null)
            .commit()
    }

    companion object {
        /**
         * sort by name, then address. sort named devices first
         */
        fun compareTo(a: BluetoothDevice, b: BluetoothDevice): Int {
            val aValid = a.name != null && !a.name.isEmpty()
            val bValid = b.name != null && !b.name.isEmpty()
            if (aValid && bValid) {
                val ret = a.name.compareTo(b.name)
                return if (ret != 0) ret else a.address.compareTo(b.address)
            }
            if (aValid) return -1
            return if (bValid) +1 else a.address.compareTo(b.address)
        }
    }
}
