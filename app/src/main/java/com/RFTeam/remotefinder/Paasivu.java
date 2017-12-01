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
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class Paasivu extends Activity {

    //private?
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.d("testi", "onConnectionStateChange:"+newState+ "status "+status);
            if (newState == BluetoothProfile.STATE_CONNECTED){
                Log.d("testi", "onConChange: Connected!");
                gatt.discoverServices();
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d("testi", "onConChange: State disconnected");
            }else{
                Log.d("testi", "onConChange: What is this state? :"+newState);
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            } else {
                Log.w("testi", "onServicesDiscovered received: " + status);
            }
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
        }
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt_, int arvo1, int arvo2){
            Log.d("testi", "rssi "+ arvo1 + " arvo2 "+arvo2);
            String text_ = selectedDevice.getName() + " Rssi: "+arvo1+" dBm";
            rssi_reading = " " +arvo1;
        }
        /*@Override
        public void onClientRegistered (BluetoothGatt gatt_, int arvo1, int arvo2){
            Log.d("testi", "rssi "+ arvo1 + " arvo2 "+arvo2);
        }*/
    };


    BluetoothGatt gatt;

    //Needed for probing for devices.
    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    //List of all devices in range.
    ArrayList<BluetoothDevice> btDeviceList = new ArrayList<BluetoothDevice>();
    //Filtered devices. (By name)
    ArrayList<BluetoothDevice> beaconList = new ArrayList<BluetoothDevice>();

    ListView deviceList;
    String rssi_reading = "";
    TextView selectedDeviceLabel;
    private Set<BluetoothDevice> pairedDevices;
    BluetoothDevice selectedDevice;
    String selectedDeviceAddress;

    private int btDevicePollFrequency = 1000;
    private Handler btHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paasivu);

        //For when we get response from pollind btDevices
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        deviceList = findViewById(R.id.deviceList);
        selectedDeviceLabel = findViewById(R.id.selectedDeviceLabel);


        /*Button but = (Button) findViewById(R.id.signalLabel);
        but.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BTAdapter.startDiscovery();
            }
        });
        **/

        TextView rssi_msg = (TextView) findViewById(R.id.signalLabel);
        if (BTAdapter == null){
            rssi_msg.setText("Device doesn't support bluetooth\n");
        }else{
            if (!BTAdapter.isEnabled()){
                rssi_msg.setText("Bluetooth not enabled\n");
            }else{
                rssi_msg.setText("Bluetooth enabled\n");
            }
        }

        btHandler = new Handler();

        startRepeatingTask();
        list();

    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                Log.d("testi", "Broadcastreceiver: new device found.");
                BluetoothDevice new_device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //Add the device if it's a new one
                if (!btDeviceList.contains(new_device)) {
                    btDeviceList.add(new_device);
                }
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                TextView rssi_msg = (TextView) findViewById(R.id.signalLabel);
                String str_ = rssi_msg.getText() + name + " => " + rssi + "dBm\n";
                rssi_msg.setText(str_);
                //connect(new_device.getAddress());
                list();
            }
        }
    };


    Runnable btUpdate = new Runnable() {
        @Override
        public void run() {
            Log.d("testi", "btupdate:");
            try{
                if(gatt != null && selectedDevice != null){
                    Log.d("testi", "btupdate: send readremoterssi");
                    gatt.readRemoteRssi();
                }else {
                    Log.d("testi", "btupdate: startdiscovery");
                    BTAdapter.startDiscovery();
                }
                if(selectedDevice != null) {
                    String str_ = selectedDevice.getName()+" signal strenght:" + rssi_reading+"dBm";
                    selectedDeviceLabel.setText(str_);
                }
            }finally {
                Log.d("testi", "btupdate:postnew");
                btHandler.postDelayed(btUpdate, btDevicePollFrequency);
            }
        }
    };

    public void list() {
        //pairedDevices = BTAdapter.getBondedDevices();
        ArrayList<String> list = new ArrayList<String>();
        for (BluetoothDevice bt : btDeviceList)//pairedDevices)
        {
            list.add(bt.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = ((TextView) view).getText().toString();
                for (BluetoothDevice bt : btDeviceList){//pairedDevices) {
                    if (bt.getName().equals(info)) {
                        selectedDevice = bt;
                        String name_ = selectedDevice.getName();
                        selectedDeviceAddress = selectedDevice.getAddress();
                        selectedDeviceLabel.setText(name_);
                        connect(bt.getAddress());
                    }
                }

            }
        });
    }

    void startRepeatingTask(){
        btUpdate.run();
    }

    void stopRepeatingTask(){
        btHandler.removeCallbacks(btUpdate);
    }


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
        if (BTAdapter == null || address == null) {
            Log.w("testi", "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (selectedDeviceAddress != null && address.equals(selectedDeviceAddress)
                && gatt != null) {
            Log.d("testi", "Trying to use an existing mBluetoothGatt for connection.");
            if (gatt.connect()) {
                Log.d("testi", "connecting. with previous gatt");
                //mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                Log.d("testi", "couldn't connect with existing gatt");
                return false;
            }
        }

        final BluetoothDevice device = BTAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w("testi", "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        gatt = device.connectGatt(this, false, mGattCallback);

        Log.d("testi", "connecting. with new gatt");
        //mConnectionState = STATE_CONNECTING;
        return true;
    }


}
