package com.android.breminder.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Bluetooth Settings
    public static final String BLUETOOTH_MONITORING_NOTIFY_KEY = "breminder_bt_timeout_notify";

    // Wifi SafeZone Settings
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final String WIFI_SAFE_ZONE_PREFS_KEY = "WifiSafeZonePrefs";
    private static final String WIFI_SAFE_ZONE_LIST_KEY = "WifiSafeZoneList";
    private static final String WIFI_SAFE_ZONE_SWITCH_STATE_KEY = "WifiSafeZoneSwitchState";

    // Switch Buttons
    private SwitchMaterial bluetoothMonitoringSwitch;
    private SwitchMaterial wifiSafeZoneSwitch;

    // Wifi SafeZone
    private final List<String> wifiSafeZoneList = new ArrayList<>();
    private ArrayAdapter<String> wifiSafeZoneListAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothMonitoringSwitch = findViewById(R.id.btMonitoringSwitch);
        wifiSafeZoneSwitch = findViewById(R.id.wifiSafeZoneSwitch);

        bluetoothMonitoringSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveBluetoothMonitoringSettings(isChecked));
        loadBluetoothMonitoringSettings();

        checkLocationPermission();
        loadWifiSafeZoneSettings();
        setupWifiSafeZoneComponents();
    }

    // --- Bluetooth Monitoring ---
    private void loadBluetoothMonitoringSettings() {
        boolean isEnabled = Settings.Global.getInt(getContentResolver(),
                BLUETOOTH_MONITORING_NOTIFY_KEY, 1) == 1;
        bluetoothMonitoringSwitch.setChecked(isEnabled);
    }

    private void saveBluetoothMonitoringSettings(boolean isEnabled) {
        int value = isEnabled ? 1 : 0;
        try {
            Settings.Global.putInt(getContentResolver(),
                    BLUETOOTH_MONITORING_NOTIFY_KEY, value);
            Log.d(TAG, "Configuração do Monitoramento bluetooth salva: " + value);
        } catch (SecurityException e) {
            bluetoothMonitoringSwitch.setChecked(!isEnabled);
            Log.e(TAG, "Falha ao salvar configuração do Monitoramento bluetooth.", e);
        }
    }


    // --- Wifi Safe Zone ---
    private void loadWifiSafeZoneSettings() {
        boolean isChecked = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .getBoolean(WIFI_SAFE_ZONE_SWITCH_STATE_KEY, false);
        wifiSafeZoneSwitch.setChecked(isChecked);

        loadWifiSafeZoneList();
    }

    private void loadWifiSafeZoneList() {
        Set<String> safeZoneSet = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .getStringSet(WIFI_SAFE_ZONE_LIST_KEY, new HashSet<>());

        wifiSafeZoneList.clear();
        wifiSafeZoneList.addAll(safeZoneSet);
    }

    private void saveWifiSafeZoneSwitchState(boolean isChecked) {
        getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(WIFI_SAFE_ZONE_SWITCH_STATE_KEY, isChecked)
                .apply();
    }

    private void saveWifiSafeZoneList() {
        Set<String> safeZoneSet = new HashSet<>(wifiSafeZoneList);

        getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(WIFI_SAFE_ZONE_LIST_KEY, safeZoneSet)
                .apply();
    }

    private void setupWifiSafeZoneComponents() {
        wifiSafeZoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveWifiSafeZoneSwitchState(isChecked);

            Intent serviceIntent = new Intent(MainActivity.this, WifiMonitorService.class);

            if (isChecked) {
                addConnectedWifiToSafeZone();
                startService(serviceIntent);
                Toast.makeText(MainActivity.this, "Monitoramento da Zona Segura iniciado.", Toast.LENGTH_SHORT).show();

            } else {
                stopService(serviceIntent);
                Toast.makeText(MainActivity.this, "Monitoramento da Zona Segura parado.", Toast.LENGTH_SHORT).show();
            }
        });

        ListView listViewWifi = findViewById(R.id.wifiSafeZoneList);
        wifiSafeZoneListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, wifiSafeZoneList);
        listViewWifi.setAdapter(wifiSafeZoneListAdapter);

        listViewWifi.setOnItemClickListener((parent, view, position, id) -> {
            // Remove SSID da SafeZoneList
            String removedSsid = wifiSafeZoneList.get(position);
            wifiSafeZoneList.remove(position);
            wifiSafeZoneListAdapter.notifyDataSetChanged();

            saveWifiSafeZoneList();

            if (wifiSafeZoneList.isEmpty()) {
                wifiSafeZoneSwitch.setChecked(false);
                saveWifiSafeZoneSwitchState(false);
            }

            Toast.makeText(this, removedSsid + " removido da Zona Segura.", Toast.LENGTH_SHORT).show();
        });
    }

    private void addConnectedWifiToSafeZone() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        String currentSsid;
        boolean isConnectedToWifi = false;

        // Verifica se há conexão ativa com Wi-Fi
        if (connManager != null) {
            Network network = connManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isConnectedToWifi = true;
                }
            }
        }

        if (isConnectedToWifi && wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();

            // Limpa as aspas do SSID
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }

            if (ssid != null && !ssid.equals("<unknown ssid>")) {
                currentSsid = ssid;
            } else {
                Toast.makeText(this, "SSID indisponível. Verifique se a Localização está ativa.", Toast.LENGTH_LONG).show();
                wifiSafeZoneSwitch.setChecked(false);
                saveWifiSafeZoneSwitchState(false);
                return;
            }
        } else {
            Toast.makeText(this, "App não está conectado a uma rede Wi-Fi.", Toast.LENGTH_LONG).show();
            wifiSafeZoneSwitch.setChecked(false);
            saveWifiSafeZoneSwitchState(false);
            return;
        }

        if (!wifiSafeZoneList.contains(currentSsid)) {
            wifiSafeZoneList.add(currentSsid);
            wifiSafeZoneListAdapter.notifyDataSetChanged();

            saveWifiSafeZoneList();
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                wifiSafeZoneSwitch.setChecked(false);
                saveWifiSafeZoneSwitchState(false);
                Toast.makeText(this, "Permissão de Localização é necessária para Zona Segura.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
