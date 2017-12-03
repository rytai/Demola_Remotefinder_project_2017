package com.RFTeam.remotefinder;

/**
 * Created by rytai on 3.12.2017.
 */

public final class STATE {

    public static int NO_BLUETOOTH = 0;
    public static int BLUETOOTH_ENABLED = 1;
    public static int BLUETOOTH_DISCOVERING = 2;
    public static int GATT_CONNECTING = 3;
    public static int GATT_CONNECTED = 4;
    public static int READING_RSSI = 5;
    public static int CURRENT_DEVICE_DISCONNECTED = 6;
    public static int DEVICE_SELECTED = 7;
    public static int CONNECTION_FAILED_RECONNECTING = 8;
    public static int UNDEFINED = 100;
}
