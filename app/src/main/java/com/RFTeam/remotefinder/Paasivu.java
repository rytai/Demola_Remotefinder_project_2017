package com.RFTeam.remotefinder;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class Paasivu extends Activity {

    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    ArrayList<BluetoothDevice> btDeviceList = new ArrayList<BluetoothDevice>();

    ListView deviceList;
    TextView selectedDeviceLabel;
    private Set<BluetoothDevice> pairedDevices;
    BluetoothDevice selectedDevice;

    private int paivitysTaajuus = 1000;
    private Handler btHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paasivu);

        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        deviceList = findViewById(R.id.deviceList);
        selectedDeviceLabel = findViewById(R.id.selectedDeviceLabel);

        list();

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
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice new_device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btDeviceList.add(new_device);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                TextView rssi_msg = (TextView) findViewById(R.id.signalLabel);
                String str_ = rssi_msg.getText() + name + " => " + rssi + "dBm\n";
                rssi_msg.setText(str_);
            }
        }
    };

    Runnable btUpdate = new Runnable() {
        @Override
        public void run() {
            try{
                BTAdapter.startDiscovery();
            }finally {
                btHandler.postDelayed(btUpdate, paivitysTaajuus);
            }
        }
    };

    public void list() {
        pairedDevices = BTAdapter.getBondedDevices();
        ArrayList<String> list = new ArrayList<String>();
        for (BluetoothDevice bt : pairedDevices)
        {
            list.add(bt.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String info = ((TextView) view).getText().toString();
                for (BluetoothDevice bt : pairedDevices) {
                    if (bt.getName().equals(info)) {
                        selectedDevice = bt;
                        selectedDeviceLabel.setText(selectedDevice.getName());
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

}
