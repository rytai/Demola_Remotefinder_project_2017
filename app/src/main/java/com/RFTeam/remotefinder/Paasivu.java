package com.RFTeam.remotefinder;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Paasivu extends Activity {

    // ############# Bluetooth ##############
    //Instance in used for Bluetooth low-energy connectivity
    //We want only one concurrent gatt-connection at a time.
    BluetoothGatt gatt;
    String gatt_connecting_to_device = "";

    //Needed for bluetooth device discovery.
    private BluetoothAdapter btAdapter;

    //List of all discovered devices. (Devices in range)
    ArrayList<BluetoothDevice> btDeviceList = new ArrayList<BluetoothDevice>();

    //Used in the gui to report signal strength
    String gui_rssi_reading = "";

    BluetoothDevice selectedDevice;
    String selectedDeviceAddress;

    private int btDevicePollFrequency = 1000;
    private Handler btDeviceDiscoveryHandler;

    //############# RSSI ##############
    private Handler RSSIReadingHandler;

    // ############# GUI ##############
    //Gets populated by bluetoothdevices
    ListView deviceListView;
    //Devices name goes in here
    TextView selectedDeviceLabel;

    private Handler UIUpdateHandler;

    //#################################
    int program_state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paasivu);

        //receiver gets responses from bluetooth device discovery
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        deviceListView = findViewById(R.id.deviceList);
        selectedDeviceLabel = findViewById(R.id.selectedDeviceLabel);

        //Textview at bottom of screen for debugigng purposes
        TextView rssi_msg = (TextView) findViewById(R.id.signalLabel);

        btDeviceDiscoveryHandler = new Handler();
        RSSIReadingHandler = new Handler();
        UIUpdateHandler = new Handler();

        //Initiate btAdapter.
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        //Check if we have bluetooth connectivity
        program_state = STATE.NO_BLUETOOTH; //Initially no connection
        // TODO: Update this on on activity reload also. Currently needs restarting the app.
        if (btAdapter == null){
            rssi_msg.setText("Device doesn't support bluetooth\n");

        }else{
            if (!btAdapter.isEnabled()){
                rssi_msg.setText("Bluetooth not enabled\n");
            }else{
                rssi_msg.setText("Bluetooth enabled\n");
                startBluetoothDeviceDiscovery();
            }
        }

        initiateDeviceListview();

        //UI update runs through the lifetime of the app.
        startUIUpdate();

    }


    //############### Bluetooth Device discovery ##################

    Runnable btDiscoveryUpdate = new Runnable() {
        @Override
        public void run() {
            try{
                Log.d("testi", "Starting bluetooth discovery");
                btAdapter.startDiscovery();
            }finally {
                //After 4 seconds call update again.
                btDeviceDiscoveryHandler.postDelayed(btDiscoveryUpdate, 4000);
            }
        }
    };

    void startBluetoothDeviceDiscovery(){
        program_state = STATE.BLUETOOTH_DISCOVERING;
        btDiscoveryUpdate.run();
    }

    //Remember to change the program state!
    void stopBluetoothDeviceDiscovery(){
        program_state = STATE.UNDEFINED;
        btDeviceDiscoveryHandler.removeCallbacks(btDiscoveryUpdate);
    }
    //Listens on responses from bluetooth device discovery
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Device found.
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                Log.d("testi", "Broadcastreceiver: new device found.");
                BluetoothDevice new_device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                //Add the device to our list of devices if it doesn't exist there.
                if (!btDeviceList.contains(new_device)) {
                    btDeviceList.add(new_device);
                }

                // Old way of getting rssi reading from device discovery
                // Left here for debugging purposes. Shows up on bottom of screen.

                //Get initial rssi reading
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                TextView rssi_msg = (TextView) findViewById(R.id.signalLabel);
                String str_ = rssi_msg.getText() + name + " => " + rssi + "dBm\n";
                rssi_msg.setText(str_);

                // Updte device list on screen.
                updateDeviceListview();
            }
        }
    };

    //###################### RSSI reading ########################

    Runnable RSSIReadingUpdate = new Runnable() {
        @Override
        public void run() {
            try{
                //List<BluetoothDevice> list_ = gatt.getConnectedDevices();
                //if (list_.contains(selectedDevice)) {
                gatt.readRemoteRssi();
                //}else{
                //    Log.d("testi", "!!!Trying to read rssi of not connected device");
                //}
            }finally {
                //After 1 seconds call update again.
                btDeviceDiscoveryHandler.postDelayed(RSSIReadingUpdate, 1000);
            }
        }
    };

    void startRSSIReading(){
        program_state = STATE.READING_RSSI;
        RSSIReadingUpdate.run();
    }

    void stopRSSIReading(){
        program_state = STATE.UNDEFINED;
        RSSIReadingHandler.removeCallbacks(RSSIReadingUpdate);
    }



    // ############## DEVICE LIST & INTERACTION ############################

    public void initiateDeviceListview() {
        ArrayList<String> list = new ArrayList<String>();
        for (BluetoothDevice bt : btDeviceList) {
            list.add(bt.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
        deviceListView.setAdapter(adapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = ((TextView) view).getText().toString();
                for (BluetoothDevice bt : btDeviceList){//pairedDevices) {
                    if (bt.getName().equals(info)) {
                        Select_Device(bt);
                    }
                }

            }
        });
    }
    public void updateDeviceListview() {
        ArrayList<String> list = new ArrayList<String>();
        for (BluetoothDevice bt : btDeviceList)//pairedDevices)
        {
            list.add(bt.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
        deviceListView.setAdapter(adapter);
    }

    public void Select_Device(BluetoothDevice btDevice_){
        stopBluetoothDeviceDiscovery();
        program_state = STATE.DEVICE_SELECTED;

        selectedDevice = btDevice_;
        selectedDeviceAddress = btDevice_.getAddress();

        String name_ = btDevice_.getName();
        selectedDeviceLabel.setText(name_);

        //Connect gatt to this device
        connect(btDevice_.getAddress());
    }

    //####################### GATT #######################


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt_, int status, int newState) {
            String intentAction;
            Log.d("testi", "onConnectionStateChange:"+newState+ "status "+status);
            if (newState == BluetoothProfile.STATE_CONNECTED){
                Log.d("testi", "onConChange: Connected!");
                program_state = STATE.GATT_CONNECTED;
                startRSSIReading();
                gatt_.discoverServices();

            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d("testi", "onConChange: State disconnected");

                //Connection failed. Try to reconnect.
                if (program_state == STATE.GATT_CONNECTING || program_state == STATE.CONNECTION_FAILED_RECONNECTING){
                    program_state = STATE.CONNECTION_FAILED_RECONNECTING;
                    connect(gatt_connecting_to_device);
                }
            }else{
                Log.d("testi", "onConChange: What is this state? :"+newState);
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            } else {
                Log.w("testi", "onServicesDiscovered received: " + status);
            }
            Log.d("testi", "Services discovered.");
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d("testi", "onCharacteristicRead"+characteristic.toString());
            Log.d("testi", "onCharacteristicReadstatus"+status);
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            Log.d("testi", "onCharacteristicChanged"+characteristic.toString());
        }        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt_, int arvo1, int arvo2){
            Log.d("testi", "rssi "+ arvo1 + " arvo2 "+arvo2);
            String text_ = selectedDevice.getName() + " Rssi: "+arvo1+" dBm";
            gui_rssi_reading = " " +arvo1;
        }
        /*@Override
        public void onClientRegistered (BluetoothGatt gatt_, int arvo1, int arvo2){
            Log.d("testi", "rssi "+ arvo1 + " arvo2 "+arvo2);
        }*/
    };


    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        program_state = STATE.GATT_CONNECTING;
        gatt_connecting_to_device = address;

        //Check that we have bluetooth and address field is not null.
        if (btAdapter == null || address == null) {
            Log.w("testi", "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Try to reconnect gatt if we're connecting to same device as last time.
        if (selectedDeviceAddress != null && address.equals(selectedDeviceAddress)
                && gatt != null) {
            Log.d("testi", "Trying to use an existing mBluetoothGatt for connection.");
            if (gatt.connect()) {
                Log.d("testi", "Connected to device with previously existing GATT");
                program_state = STATE.GATT_CONNECTED;
                return true;
            } else {
                Log.d("testi", "couldn't connect with existing gatt");
                program_state = STATE.CURRENT_DEVICE_DISCONNECTED;
                return false;
            }
        }

        // There is no previous connection -> Create a new one.
        final BluetoothDevice device = btAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w("testi", "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        gatt = device.connectGatt(this, false, mGattCallback);
        program_state = STATE.GATT_CONNECTING;

        Log.d("testi", "connecting. with new gatt");
        //mConnectionState = STATE_CONNECTING;
        return true;
    }

    //####################### UI #######################


    Runnable UIUpdate = new Runnable() {
        @Override
        public void run() {
            try{
                if (program_state == STATE.READING_RSSI) {
                    String str_ = selectedDevice.getName() +" "+gui_rssi_reading;
                            selectedDeviceLabel.setText(str_);
                }else if(program_state == STATE.BLUETOOTH_DISCOVERING){
                    selectedDeviceLabel.setText("Discovering bluetooth devices");
                }else if(program_state == STATE.GATT_CONNECTING){
                    selectedDeviceLabel.setText("Connecting to device.");
                }else if(program_state == STATE.CONNECTION_FAILED_RECONNECTING){
                    selectedDeviceLabel.setText("Connection failed. Retrying.");
                }



            }finally {
                //After 1 seconds call update again.
                btDeviceDiscoveryHandler.postDelayed(UIUpdate, 1000);
            }
        }
    };

    void startUIUpdate(){
        UIUpdate.run();
    }

    //Obsolete
    void stopUIUpdate(){
        UIUpdateHandler.removeCallbacks(UIUpdate);
    }

}
