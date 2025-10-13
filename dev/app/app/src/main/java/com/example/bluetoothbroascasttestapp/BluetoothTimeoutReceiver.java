package com.example.bluetoothbroascasttestapp;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BluetoothTimeoutReceiver extends BroadcastReceiver {
    private static final String TAG = "TimeoutReceiver";
    public static final String ACTION_BLUETOOTH_TIMEOUT = "com.bluetooth.timeoutreceiver.BLUETOOTH_TIMEOUT";
    public static final String EXTRA_DEVICE_NAME = "device_name";
    private static final String EXTRA_DISCONNECT_REASON = "android.bluetooth.device.extra.disconnect_reason";
    private static final int DISCONNECT_REASON_TIMEOUT = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int reason = intent.getIntExtra(EXTRA_DISCONNECT_REASON, -1);

            if (reason == DISCONNECT_REASON_TIMEOUT) {
                Log.w(TAG, "Desconectado por distância.");
                handleDistanceDisconnection(context, device);
            } else if (reason == -1) {
                Log.i(TAG, "Default Value.");
            } else {
                Log.i(TAG, "Desconectado por outra razão (Código: " + reason + ")");
            }
        }
    }

    private void handleDistanceDisconnection(Context context, BluetoothDevice device) {
        Intent intent = new Intent(ACTION_BLUETOOTH_TIMEOUT);
        intent.putExtra(EXTRA_DEVICE_NAME, device);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
