package com.android.breminder.settings;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

public class WifiMonitorService extends Service {

    private static final String TAG = "WifiMonitorService";

    private static final String BLUETOOTH_MONITORING_NOTIFY_KEY = "breminder_bt_timeout_notify";
    private static final String WIFI_SAFE_ZONE_PREFS_KEY = "WifiSafeZonePrefs";
    private static final String WIFI_SAFE_ZONE_LIST_KEY = "WifiSafeZoneList";
    private static final String WIFI_SAFE_ZONE_SWITCH_STATE_KEY = "WifiSafeZoneSwitchState";

    private WifiStateReceiver wifiReceiver;
    private final Set<String> wifiSafeZoneList = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created. Starting monitoring.");

        loadWifiSafeZoneList();

        wifiReceiver = new WifiStateReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadWifiSafeZoneList();
        checkWifiAndToggleBluetoothTimeout();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiReceiver);
        Log.d(TAG, "Service Destroyed. Monitoring stopped.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                new Handler(Looper.getMainLooper()).postDelayed(WifiMonitorService.this::checkWifiAndToggleBluetoothTimeout, 1000);
            }
        }
    }

    private boolean isWifiSafeZoneEnabled() {
        return getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .getBoolean(WIFI_SAFE_ZONE_SWITCH_STATE_KEY, false);
    }

    private void loadWifiSafeZoneList() {
        Set<String> safeZoneSet = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .getStringSet(WIFI_SAFE_ZONE_LIST_KEY, new HashSet<>());

        wifiSafeZoneList.clear();
        wifiSafeZoneList.addAll(safeZoneSet);
    }

    private void checkWifiAndToggleBluetoothTimeout() {
        if (!isWifiSafeZoneEnabled()) {
            return;
        }

        boolean btMonitoringEnabled = Settings.Global.getInt(getContentResolver(), BLUETOOTH_MONITORING_NOTIFY_KEY, 1) == 1;

        if (btMonitoringEnabled) {
            String currentSsid = getConnectedWifiName();
            loadWifiSafeZoneList();

            saveBluetoothMonitoringSettings(currentSsid != null && !wifiSafeZoneList.contains(currentSsid));
        }

    }

    private String getConnectedWifiName() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connManager != null) {
            Network network = connManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (wifiManager != null) {
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        String ssid = wifiInfo.getSSID();

                        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid = ssid.substring(1, ssid.length() - 1);
                        }

                        if (ssid != null && !ssid.equals("<unknown ssid>")) {
                            return ssid;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void saveBluetoothMonitoringSettings(boolean isEnabled) {
        int value = isEnabled ? 1 : 0;
        try {
            Settings.Global.putInt(getContentResolver(),
                    BLUETOOTH_MONITORING_NOTIFY_KEY, value);
            Log.d(TAG, "Configuração do Monitoramento bluetooth salva: " + value);
        } catch (SecurityException e) {
            Log.e(TAG, "Falha ao salvar configuração do Monitoramento bluetooth.", e);
        }
    }
}