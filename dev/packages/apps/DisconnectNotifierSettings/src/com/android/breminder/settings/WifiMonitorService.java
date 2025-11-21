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

    // Usamos a constante da MainActivity para garantir consistência na chave
    private static final String SETTING_BLUETOOTH_TIMEOUT_NOTIFY = MainActivity.SETTING_BLUETOOTH_TIMEOUT_NOTIFY;
    
    // Chaves locais (SharedPreferences)
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

        // Registra o receiver para mudanças de conectividade (troca de wifi, desconexão, etc)
        wifiReceiver = new WifiStateReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Sempre que o serviço é iniciado (ou reiniciado pelo toggle), verificamos o estado atual
        loadWifiSafeZoneList();
        checkWifiAndToggleBluetoothTimeout();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
        Log.d(TAG, "Service Destroyed. Monitoring stopped.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Receiver que ouve mudanças na rede e agenda uma verificação.
     */
    private class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                // Delay pequeno para garantir que a conexão Wifi estabilizou e o SSID está disponível
                new Handler(Looper.getMainLooper()).postDelayed(WifiMonitorService.this::checkWifiAndToggleBluetoothTimeout, 2000);
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

    /**
     * Lógica principal:
     * 1. Pega o Wifi atual.
     * 2. Verifica se está na lista segura.
     * 3. Ativa ou Desativa a notificação de Bluetooth baseado nisso.
     */
    private void checkWifiAndToggleBluetoothTimeout() {
        if (!isWifiSafeZoneEnabled()) {
            Log.d(TAG, "Zona segura desativada. Ignorando verificação.");
            return;
        }

        String currentSsid = getConnectedWifiName();
        loadWifiSafeZoneList(); // Recarrega para garantir lista atualizada

        // LÓGICA:
        // Se estivermos conectados a um Wifi SEGURO -> DESATIVAR notificação (false)
        // Se estivermos desconectados ou em Wifi NÃO SEGURO -> ATIVAR notificação (true)
        
        boolean isConnectedToSafeZone = currentSsid != null && wifiSafeZoneList.contains(currentSsid);
        boolean shouldEnableBluetoothNotify = !isConnectedToSafeZone;

        Log.d(TAG, "Wifi Atual: " + currentSsid + " | Na Zona Segura: " + isConnectedToSafeZone + " | Definindo Bluetooth Notify para: " + shouldEnableBluetoothNotify);

        saveBluetoothMonitoringSettings(shouldEnableBluetoothNotify);
    }

    private String getConnectedWifiName() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sem permissão de localização para ler SSID.");
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

                        // Remove aspas do SSID se existirem (comportamento padrão do Android)
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
            // Verifica o estado atual para não escrever no banco de dados sem necessidade
            int current = Settings.Global.getInt(getContentResolver(), SETTING_BLUETOOTH_TIMEOUT_NOTIFY, -1);
            
            if (current != value) {
                Settings.Global.putInt(getContentResolver(),
                        SETTING_BLUETOOTH_TIMEOUT_NOTIFY, value);
                Log.d(TAG, "Configuração do Monitoramento bluetooth atualizada para: " + value);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Falha ao salvar configuração do Monitoramento bluetooth.", e);
        }
    }
}