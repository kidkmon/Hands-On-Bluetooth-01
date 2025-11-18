package com.example.bluetoothbroascasttestapp;

import android.Manifest;
import android.content.Context;
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
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    public static final String SETTING_BLUETOOTH_TIMEOUT_NOTIFY = "breminder_bt_timeout_notify";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private TextView textViewWifiName;
    private Button buttonGetWifi;
    private ListView listViewWifi;
    private SwitchMaterial mTimeoutSwitch;
    private SwitchMaterial mSafeZoneSwitch;

    private List<String> wifiHistoryList = new ArrayList<>();
    private ArrayAdapter<String> wifiListAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTimeoutSwitch = findViewById(R.id.switch_timeout_notifier);
        mSafeZoneSwitch = findViewById(R.id.switch_safe_zone_wifi);

        mTimeoutSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting(isChecked));

        loadSettings();
        setupWifiComponents();
        checkLocationPermission();
    }

    // --- Bluetooth ---

    /**
     * Carrega o valor atual do Settings.Global e atualiza o toggle.
     */
    private void loadSettings() {
        boolean isEnabled = Settings.Global.getInt(getContentResolver(),
                SETTING_BLUETOOTH_TIMEOUT_NOTIFY, 1) == 1;

        mTimeoutSwitch.setChecked(isEnabled);
        Log.d(TAG, "Configuração carregada: " + isEnabled);
    }

    /**
     * Salva o novo estado (on/off) no Settings.Global.
     */
    private void saveSetting(boolean isEnabled) {
        int value = isEnabled ? 1 : 0;
        try {
            Settings.Global.putInt(getContentResolver(),
                    SETTING_BLUETOOTH_TIMEOUT_NOTIFY, value);

            Log.d(TAG, "Configuração salva: " + value);
        } catch (SecurityException e) {
            // Este erro ocorre se o app não tiver a permissão WRITE_SECURE_SETTINGS,
            // que geralmente só é dada a apps do sistema (priv-app).
            Log.e(TAG, "FALHA AO SALVAR CONFIGURAÇÃO! Verifique as permissões.", e);
            mTimeoutSwitch.setChecked(!isEnabled);
        }
    }


    // --- Wifi ---
    private void setupWifiComponents() {
        textViewWifiName = findViewById(R.id.textViewWifiName);
        buttonGetWifi = findViewById(R.id.buttonGetWifi);
        listViewWifi = findViewById(R.id.listViewWifi);

        wifiListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, wifiHistoryList);
        listViewWifi.setAdapter(wifiListAdapter);

        buttonGetWifi.setOnClickListener(v -> {
            checkLocationPermission();
        });
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getConnectedWifiName();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getConnectedWifiName();
            } else {
                textViewWifiName.setText("Permissão de Localização negada.");
                Toast.makeText(this, "Permissão de Localização é necessária.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getConnectedWifiName() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        String currentSsid = "Não conectado ao Wifi ou SSID indisponível";
        boolean isConnectedToWifi = false;

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

            Log.d(TAG, "SSID bruto obtido: " + ssid);

            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }

            if (ssid != null && !ssid.equals("<unknown ssid>")) {
                currentSsid = ssid;
            } else {
                currentSsid = "SSID Indisponível (Verifique se a Localização/GPS do dispositivo está LIGADA)";
                Log.w(TAG, "SSID não obtido: O serviço de Localização deve estar ativado.");
            }
        }

        textViewWifiName.setText(currentSsid);

        if (wifiHistoryList.isEmpty() || !wifiHistoryList.get(wifiHistoryList.size() - 1).equals(currentSsid)) {
            wifiHistoryList.add(currentSsid);
            wifiListAdapter.notifyDataSetChanged();
        }
    }
}