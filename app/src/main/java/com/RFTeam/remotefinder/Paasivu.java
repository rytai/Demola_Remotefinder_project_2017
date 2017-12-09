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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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

    //######## Gatt retrying when timeout occurs
    private Handler GattConnectWithTimeoutHandler;

    //############# RSSI ##############
    private String gattDeviceAddressToConnect;
    private Handler RSSIReadingHandler;

    // ############# GUI ##############
    //Gets populated by bluetoothdevices
    ListView deviceListView;
    //Devices name goes in here
    TextView selectedDeviceLabel;

    private Handler UIUpdateHandler;

    int rssi_readings_iterator = 0;
    final int rssi_readings_count = 20;
    int lastRSSI_Readings[] = new int[rssi_readings_count];

    //#################################
    int program_state;

    // TODO: On resumeen samat

    @Override
    protected void onStop() {
        stopRSSIReading();
        stopUIUpdate();
        super.onStop();
        gatt.disconnect();
        gatt.close();
        btAdapter.cancelDiscovery();

    }
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
        GattConnectWithTimeoutHandler = new Handler();

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
                //startBluetoothDeviceDiscovery();
                btAdapter.startDiscovery();
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
                btDeviceDiscoveryHandler.postDelayed(btDiscoveryUpdate, 10000);
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
                btDeviceDiscoveryHandler.postDelayed(RSSIReadingUpdate, 100);
            }
        }
    };

    void startRSSIReading(){
        program_state = STATE.READING_RSSI;
        Log.d("testi", "Starting to rad rssi readings.");
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
        //stopBluetoothDeviceDiscovery();
        program_state = STATE.DEVICE_SELECTED;

        selectedDevice = btDevice_;
        selectedDeviceAddress = btDevice_.getAddress();

        String name_ = btDevice_.getName();

        gattDeviceAddressToConnect = btDevice_.getAddress();

        //stopGattConnectingWithTimeout();
        try {
            stopGattConnectingWithTimeout();
        }catch (Exception exception){
            
        }
        startGattConnectingWithTimeout();

        //Connect gatt to this device
        //connect(btDevice_.getAddress());
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
                stopGattConnectingWithTimeout();
                startRSSIReading();
                //gatt_.discoverServices(); this might(?) cause issues

            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d("testi", "onConChange: State disconnected");
                //stopGattConnectingWithTimeout();
                gatt.disconnect();
                gatt.close();

                //Connection failed. Try to reconnect.
                if (program_state == STATE.GATT_CONNECTING | program_state == STATE.CONNECTION_FAILED_RECONNECTING){
                    program_state = STATE.CONNECTION_FAILED_RECONNECTING;
                    //startGattConnectingWithTimeout();
                    //connect(gatt_connecting_to_device);
                }
                //connect(gatt_connecting_to_device);
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
            //Log.d("testi", "rssi "+ arvo1 + " arvo2 "+arvo2);
            String text_ = selectedDevice.getName() + " Rssi: "+arvo1+" dBm";
            gui_rssi_reading = " " +arvo1;
            lastRSSI_Readings[rssi_readings_iterator] = arvo1;
            if (rssi_readings_iterator == rssi_readings_count - 1){
                rssi_readings_iterator = 0;
            }else{
                rssi_readings_iterator +=1;
            }
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
            Log.d("testi", "Gatt exists. Closing it and starting a new one.");
            /*if (gatt.connect()) {
                Log.d("testi", "Connected to device with previously existing GATT");
                program_state = STATE.GATT_CONNECTED;
                return true;
            } else {
                Log.d("testi", "couldn't connect with existing gatt");
                program_state = STATE.CURRENT_DEVICE_DISCONNECTED;
                return false;
            }
            */
            gatt.disconnect();
            gatt.close();
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

    //########### Gatt connection timeout ##############

    Runnable GattConnectWithTimeout = new Runnable() {
        @Override
        public void run() {
            try{
                if(gatt != null) {
                    gatt.disconnect();
                    gatt.close();
                }
                connect(gattDeviceAddressToConnect);
            }finally {
                //After 1 seconds call update again.
                GattConnectWithTimeoutHandler.postDelayed(GattConnectWithTimeout, 1300);
            }
        }
    };

    void startGattConnectingWithTimeout(){
        program_state = STATE.GATT_CONNECTING;
        Log.d("testi", "Starting to connect gatt with timeout");
        GattConnectWithTimeout.run();
    }

    void stopGattConnectingWithTimeout(){
        GattConnectWithTimeoutHandler.removeCallbacks(GattConnectWithTimeout);
    }

    //####################### UI #######################


    Runnable UIUpdate = new Runnable() {
        @Override
        public void run() {
            try{
                if (program_state == STATE.READING_RSSI) {
                    // TODO: Show averaged value instead of last one. work-on-progress:
                    int sum = 0;
                    for (int i = 0;  i < rssi_readings_count; i++){
                        sum += lastRSSI_Readings[i];
                    }
                    float average = (float)sum / (float)rssi_readings_count;

                    String str_ = "RSSI: "+selectedDevice.getName() +" "+gui_rssi_reading;
                    str_ = str_ + "\nAverage: "+ average;
                    selectedDeviceLabel.setText(str_);

                }else if(program_state == STATE.BLUETOOTH_DISCOVERING){
                    selectedDeviceLabel.setText("Discovering bluetooth devices");
                }else if(program_state == STATE.GATT_CONNECTING){
                    selectedDeviceLabel.setText("Connecting to device.");
                }else if(program_state == STATE.CONNECTION_FAILED_RECONNECTING){
                    selectedDeviceLabel.setText("Connection failed. Retrying.");
                }else if(program_state == STATE.CURRENT_DEVICE_DISCONNECTED){
                    selectedDeviceLabel.setText("Disconnected");
                }else if(program_state == STATE.DEVICE_SELECTED){
                    selectedDeviceLabel.setText("Starting to connect.");
                }



            }finally {
                //After 1 seconds call update again.
                btDeviceDiscoveryHandler.postDelayed(UIUpdate, 100);
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
