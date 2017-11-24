package com.RFTeam.remotefinder;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;

import javax.net.ssl.HandshakeCompletedEvent;

public class Paasivu extends Activity {

    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    ArrayList<BluetoothDevice> btDeviceList = new ArrayList<BluetoothDevice>();

    private int paivitysTaajuus = 1000;
    private Handler btHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paasivu);

        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

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
                String str_ = rssi_msg.getText() + name + " => " +rssi + "dBm\n";
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

    Runnable posUpdate = new Runnable() {
        @Override
        public void run() {
            ImageView remote = (ImageView) findViewById(R.id.remote);
            ImageView user = (ImageView) findViewById(R.id.user);
            remote.setX(user.getX());
            remote.setY(user.getY());
        }
    };

    void startRepeatingTask(){
        btUpdate.run();
        posUpdate.run();
    }

    void stopRepeatingTask(){
        btHandler.removeCallbacks(btUpdate);
    }

}
