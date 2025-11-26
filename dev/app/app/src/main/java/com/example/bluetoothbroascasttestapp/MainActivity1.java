package com.example.bluetoothbroascasttestapp;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity1 extends AppCompatActivity {

    private static final String TAG = "BluetoothBroadcast";
    // Código de requisição para múltiplas permissões
    private static final int REQUEST_ALL_PERMISSIONS = 101;

    public static final String PREFS_NAME = "MonitoramentoPrefs";
    public static final String KEY_MONITORAMENTO_ENABLED = "key_monitoring_enabled";
    public static final String KEY_WIFI_LIST_JSON = "key_wifi_list_json";

    //private BluetoothTimeoutReceiver bluetoothTimeoutReceiver;
    private TextView statusTextView;
    private BroadcastReceiver timeoutMessageReceiver;

    private RecyclerView wifiRecyclerView;
    private WifiListAdapter wifiAdapter;
    private ArrayList<String> wifiList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main1);

//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.textView2), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });

        setupLocalTimeoutReceiver();

        //bluetoothTimeoutReceiver = new BluetoothTimeoutReceiver();

        // Verifica TODAS as permissões necessárias (BT + Localização) ao iniciar
        checkAndRequestAllPermissions();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // --- Lógica do Switch Bluetooth ---
        SwitchMaterial switchBluetooth = findViewById(R.id.btMonitoringSwitch);
        if (switchBluetooth != null) {
            boolean isSwitchEnabled = prefs.getBoolean(KEY_MONITORAMENTO_ENABLED, false);
            switchBluetooth.setChecked(isSwitchEnabled);

            switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putBoolean(KEY_MONITORAMENTO_ENABLED, isChecked);
                editor.apply();
            });
        }

        // --- INÍCIO DA LÓGICA WI-FI ---

        SwitchMaterial switchWifi = findViewById(R.id.wifiSafeZoneSwitch);
        wifiRecyclerView = findViewById(R.id.wifi_recycler_view);

        // Carrega a lista salva anteriormente (JSON)
        loadWifiListFromJson();

        wifiAdapter = new WifiListAdapter(wifiList);
        wifiRecyclerView.setAdapter(wifiAdapter);
        wifiRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        wifiRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        if (switchWifi != null) {
            switchWifi.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Chama o método que contém a lógica da sua equipe
                    String realWifiName = getWifiNameFromTeamLogic();

                    if (realWifiName != null) {
                        wifiList.add(realWifiName);

                        // Atualiza a tela
                        wifiAdapter.notifyItemInserted(wifiList.size() - 1);
                        wifiRecyclerView.scrollToPosition(wifiList.size() - 1);

                        // Salva a nova lista
                        saveWifiListToJson();

                        Log.d(TAG, "Rede adicionada: " + realWifiName);
                    } else {
                        // Caso não esteja conectado ou sem permissão
                        Toast.makeText(MainActivity1.this, "Nenhuma rede Wi-Fi detectada ou sem permissão.", Toast.LENGTH_SHORT).show();
                        // Desliga o switch visualmente para indicar falha
                        buttonView.setChecked(false);
                    }
                }
            });
        }
    }

    // --------------------------------------------------------------------------
    // INTEGRAÇÃO COM A EQUIPE
    // --------------------------------------------------------------------------

    private String getWifiNameFromTeamLogic() {
        // Redireciona para o método que implementa a lógica real
        return getConnectedWifiName();
    }

    /**
     * Método baseado na lógica fornecida pela equipe (WifiMonitorService).
     * Retorna o SSID da rede atual ou null.
     */
    private String getConnectedWifiName() {
        // Verifica permissão de localização (Obrigatória para ler SSID no Android 8.1+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sem permissão de localização para ler SSID.");
            // Tenta pedir a permissão se estiver faltando
            checkAndRequestAllPermissions();
            return null;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connManager != null) {
            Network network = connManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connManager.getNetworkCapabilities(network);
                // Verifica se é realmente Wi-Fi
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

    // --------------------------------------------------------------------------

    private void saveWifiListToJson() {
        JSONArray jsonArray = new JSONArray();
        for (String wifiName : wifiList) {
            jsonArray.put(wifiName);
        }
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_WIFI_LIST_JSON, jsonArray.toString());
        editor.apply();
    }

    private void loadWifiListFromJson() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_WIFI_LIST_JSON, "[]");
        wifiList.clear();
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                wifiList.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Erro ao ler JSON da lista", e);
        }
    }

    // --- Permissões (Atualizado para incluir Localização) ---

    private void checkAndRequestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Permissão de Bluetooth (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        // Permissão de Localização (Necessária para ler SSID do Wi-Fi)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_ALL_PERMISSIONS);
        } else {
            // Se já tem permissão de BT, registra o receiver
            registerBluetoothReceiver();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            // Verifica se as permissões foram concedidas
            // Se houver resultados e o primeiro for concedido
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permissões concedidas.");
                registerBluetoothReceiver();
            } else {
                Log.e(TAG, "Permissões negadas.");
                Toast.makeText(this, "Permissões necessárias para funcionar.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- Restante do código (Receivers, Adapter, etc) ---

    private void setupLocalTimeoutReceiver() {
        timeoutMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                if (BluetoothTimeoutReceiver.ACTION_BLUETOOTH_TIMEOUT.equals(intent.getAction())) {
//                    String deviceName = intent.getStringExtra(BluetoothTimeoutReceiver.EXTRA_DEVICE_NAME);
//                    String message = deviceName + " se desconectou por distância! (Timeout)";
//                    if (statusTextView != null) {
//                        statusTextView.setText(message);
//                    }
//                }
            }
        };
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try {
            //registerReceiver(bluetoothTimeoutReceiver, filter);
        } catch (SecurityException e) {
            Log.e(TAG, "ERRO: Falha ao registrar o Receiver.", e);
        }
    }

    private void unregisterBluetoothReceiver() {
//        if (bluetoothTimeoutReceiver != null) {
//            try {
//                unregisterReceiver(bluetoothTimeoutReceiver);
//            } catch (IllegalArgumentException e) {
//                Log.w(TAG, "Receiver já foi desregistrado.");
//            }
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //LocalBroadcastManager.getInstance(this).registerReceiver(timeoutMessageReceiver, new IntentFilter(BluetoothTimeoutReceiver.ACTION_BLUETOOTH_TIMEOUT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timeoutMessageReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterBluetoothReceiver();
    }

    // --- Adapter do RecyclerView ---
    public class WifiListAdapter extends RecyclerView.Adapter<WifiListAdapter.WifiViewHolder> {
        private List<String> mWifiList;
        public WifiListAdapter(List<String> wifiList) { this.mWifiList = wifiList; }

        public class WifiViewHolder extends RecyclerView.ViewHolder {
            TextView wifiNameTextView;
            public WifiViewHolder(@NonNull View itemView) {
                super(itemView);
                wifiNameTextView = itemView.findViewById(R.id.wifi_name_textview);
            }
        }

        @NonNull
        @Override
        public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_wifi, parent, false);
            return new WifiViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WifiViewHolder holder, int position) {
            holder.wifiNameTextView.setText(mWifiList.get(position));
        }

        @Override
        public int getItemCount() { return mWifiList.size(); }
    }
}