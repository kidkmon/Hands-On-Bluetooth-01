package com.android.breminder.settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DisconnectNotifierSettings";

    // --- Configurações do Bluetooth (Settings.Global) ---
    // A "chave" global para nossa configuracao. Usamos 1 como padrao (ativado).
    public static final String SETTING_BLUETOOTH_TIMEOUT_NOTIFY = "breminder_bt_timeout_notify";

    // --- Configurações da Zona Segura Wifi (SharedPreferences) ---
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final String WIFI_SAFE_ZONE_PREFS_KEY = "WifiSafeZonePrefs";
    private static final String WIFI_SAFE_ZONE_LIST_KEY = "WifiSafeZoneList";
    private static final String WIFI_SAFE_ZONE_SWITCH_STATE_KEY = "WifiSafeZoneSwitchState";

    // Elementos de UI
    private SwitchMaterial mTimeoutSwitch;
    private SwitchMaterial mSafeZoneSwitch;
    
    // Elementos da Lista de Wifi (Futuro: Adicionar ListView no seu XML se quiser exibir)
    private ListView mWifiSafeZoneListView; 
    private ArrayAdapter<String> mWifiSafeZoneListAdapter;
    private List<String> mWifiSafeZoneList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa Views
        mTimeoutSwitch = findViewById(R.id.btMonitoringSwitch);     // ID corrigido
        mSafeZoneSwitch = findViewById(R.id.wifiSafeZoneSwitch);    // ID corrigido
        mWifiSafeZoneListView = findViewById(R.id.wifiSafeZoneList);// ID corrigido

        // --- Configuração Bluetooth ---
        loadBluetoothSettings();

        mTimeoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveBluetoothSetting(isChecked);
            }
        });

        // --- Configuração Wifi Seguro ---
        setupWifiSafeZoneList(); // Configura a lista e adapter
        loadWifiSafeZoneSettings(); // Carrega estado salvo do switch e da lista

        mSafeZoneSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Ao ativar, precisamos verificar permissão de localização para ler SSID
                    checkLocationPermission();
                } else {
                    stopWifiMonitorService();
                    saveWifiSafeZoneSwitchState(false);

                    // ***** CORRECAO *****
                    // Se desligamos a Zona Segura, precisamos restaurar o estado do
                    // Monitoramento Bluetooth baseando-se no botao principal.
                    // Se o botao principal estiver LIGADO, forcamos a volta para 1.
                    boolean isMainSwitchOn = mTimeoutSwitch.isChecked();
                    saveBluetoothSetting(isMainSwitchOn);
                    
                    Log.d(TAG, "Zona Segura desligada. Restaurando configuração Bluetooth para: " + isMainSwitchOn);
                }
            }
        });
        
        // Habilita o botão pois agora temos lógica implementada
        mSafeZoneSwitch.setEnabled(true);
    }

    // =================================================================================
    // LÓGICA DO BLUETOOTH (Settings.Global)
    // =================================================================================

    /**
     * Carrega o valor atual do Settings.Global e atualiza o toggle do Bluetooth.
     */
    private void loadBluetoothSettings() {
        boolean isEnabled = Settings.Global.getInt(getContentResolver(), 
                                SETTING_BLUETOOTH_TIMEOUT_NOTIFY, 1) == 1; // 1 = Padrao ATIVADO
        
        mTimeoutSwitch.setChecked(isEnabled);
        Log.d(TAG, "Configuração Bluetooth carregada: " + isEnabled);
    }

    /**
     * Salva o novo estado (on/off) no Settings.Global.
     */
    private void saveBluetoothSetting(boolean isEnabled) {
        int value = isEnabled ? 1 : 0;
        try {
            Settings.Global.putInt(getContentResolver(), 
                                SETTING_BLUETOOTH_TIMEOUT_NOTIFY, value);
            
            Log.d(TAG, "Configuração Bluetooth salva: " + value);
        } catch (SecurityException e) {
            Log.e(TAG, "FALHA AO SALVAR CONFIGURAÇÃO! Verifique as permissões de priv-app.", e);
        }
    }

    // =================================================================================
    // LÓGICA DO WIFI SEGURO (SharedPreferences + Service)
    // =================================================================================

    private void setupWifiSafeZoneList() {
        mWifiSafeZoneList = new ArrayList<>();
        mWifiSafeZoneListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mWifiSafeZoneList);
        
        // Se o ListView existir no layout, define o adapter
        if (mWifiSafeZoneListView != null) {
            mWifiSafeZoneListView.setAdapter(mWifiSafeZoneListAdapter);

            mWifiSafeZoneListView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    String wifiToRemove = mWifiSafeZoneList.get(position);
                    
                    mWifiSafeZoneList.remove(position);
                    mWifiSafeZoneListAdapter.notifyDataSetChanged();
                    
                    saveWifiSafeZoneList();
                    
                    Toast.makeText(MainActivity.this, "Removido: " + wifiToRemove, Toast.LENGTH_SHORT).show();
                    
                    if (mSafeZoneSwitch.isChecked()) {
                        startWifiMonitorService();
                    }
                    
                    return true; 
                }
            });
        }
    }

    private void loadWifiSafeZoneSettings() {
        SharedPreferences prefs = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE);
        
        // Carrega estado do Switch
        boolean isSwitchOn = prefs.getBoolean(WIFI_SAFE_ZONE_SWITCH_STATE_KEY, false);
        mSafeZoneSwitch.setChecked(isSwitchOn);

        // Carrega lista de Wifi salvos
        Set<String> savedSet = prefs.getStringSet(WIFI_SAFE_ZONE_LIST_KEY, new HashSet<>());
        mWifiSafeZoneList.clear();
        mWifiSafeZoneList.addAll(savedSet);
        mWifiSafeZoneListAdapter.notifyDataSetChanged();

        // Se estava ativado, garante que o serviço inicie
        if (isSwitchOn) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startWifiMonitorService();
            } else {
                // Se perdemos a permissão, desliga o switch visualmente
                mSafeZoneSwitch.setChecked(false);
            }
        }
    }

    private void saveWifiSafeZoneSwitchState(boolean isChecked) {
        SharedPreferences prefs = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(WIFI_SAFE_ZONE_SWITCH_STATE_KEY, isChecked);
        editor.apply();
    }

    private void saveWifiSafeZoneList() {
        SharedPreferences prefs = getSharedPreferences(WIFI_SAFE_ZONE_PREFS_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> set = new HashSet<>(mWifiSafeZoneList);
        editor.putStringSet(WIFI_SAFE_ZONE_LIST_KEY, set);
        editor.apply();
    }

    /**
     * Método público para adicionar o Wifi atual à lista segura (ex: chamado por um botão "Adicionar Wifi Atual")
     */
    public void addCurrentWifiToSafeZone() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        String currentSsid = "";

        if (connManager != null) {
            Network network = connManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    currentSsid = wifiInfo.getSSID();
                    // Remove aspas do SSID se existirem
                    if (currentSsid != null && currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                        currentSsid = currentSsid.substring(1, currentSsid.length() - 1);
                    }
                }
            }
        }

        if (currentSsid == null || currentSsid.isEmpty() || currentSsid.equals("<unknown ssid>")) {
            Toast.makeText(this, "Não conectado a uma rede Wi-Fi válida.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mWifiSafeZoneList.contains(currentSsid)) {
            mWifiSafeZoneList.add(currentSsid);
            mWifiSafeZoneListAdapter.notifyDataSetChanged();
            saveWifiSafeZoneList();
            Toast.makeText(this, "Wifi adicionado: " + currentSsid, Toast.LENGTH_SHORT).show();
            
            // Se o serviço estiver rodando, ele vai ler a lista atualizada automaticamente ou precisa ser reiniciado
            if (mSafeZoneSwitch.isChecked()) {
                startWifiMonitorService(); 
            }
        } else {
            Toast.makeText(this, "Wifi já está na lista segura.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Gerenciamento do Serviço ---

    private void startWifiMonitorService() {
        Intent serviceIntent = new Intent(this, WifiMonitorService.class);
        startService(serviceIntent);
        Log.d(TAG, "WifiMonitorService iniciado.");
    }

    private void stopWifiMonitorService() {
        Intent serviceIntent = new Intent(this, WifiMonitorService.class);
        stopService(serviceIntent);
        Log.d(TAG, "WifiMonitorService parado.");
    }

    // --- Permissões ---

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permissão já concedida
            saveWifiSafeZoneSwitchState(true);
            startWifiMonitorService();

            // linha dicionada para adicionar o wifi quando ligar o switch
            addCurrentWifiToSafeZone();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida pelo usuário
                saveWifiSafeZoneSwitchState(true);
                startWifiMonitorService();

                // linha dicionada para adicionar o wifi quando dar permissao
                addCurrentWifiToSafeZone();
            } else {
                // Permissão negada
                mSafeZoneSwitch.setChecked(false);
                saveWifiSafeZoneSwitchState(false);
                Toast.makeText(this, "Permissão de Localização é necessária para identificar a rede Wi-Fi.", Toast.LENGTH_LONG).show();
            }
        }
    }
}