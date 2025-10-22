package com.example.bluetoothbroascasttestapp;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Switch;
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


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothBroadcast";
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 101;

    private BluetoothTimeoutReceiver bluetoothTimeoutReceiver;
    private TextView statusTextView;
    private BroadcastReceiver timeoutMessageReceiver;

    // Nome do arquivo onde as preferencias serao salvas
    public static final String PREFS_NAME = "MonitoramentoPrefs";

    // Variavel com o valor booleano true/false
    public static final String KEY_MONITORAMENTO_ENABLED = "key_monitoramento_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.status_message);
        setupLocalTimeoutReceiver();

        bluetoothTimeoutReceiver = new BluetoothTimeoutReceiver();
        checkAndRequestBluetoothPermissions();

        Switch switchBluetooth = findViewById(R.id.switchBluetooth);

        if (switchBluetooth == null) {
            Log.e(TAG, "Erro: Não foi possível encontrar o Switch! Verifique o ID no XML.");
            return;
        }

        // Inicio da logica de persistencia

        // 1 - Carregar o valor salvo
        // Obtem o arquivo de SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Le o valor salvo
        boolean isSwitchEnabled = prefs.getBoolean(KEY_MONITORAMENTO_ENABLED, false);

        // 2 - Atualizar a UI
        // Define o estado visual do switch com o valor salvo
        switchBluetooth.setChecked(isSwitchEnabled);

        // 3 - Salvar as mudancas de estado do switch
        switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                Log.d(TAG, "Monitoramento Inteligente: ATIVADO");
            } else {
                Log.d(TAG, "Monitoramento Inteligente: DESATIVADO");
            }

            // Obtem o editor para escrever no SharedPreferences
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();

            // Salva o novo valor de isChecked
            editor.putBoolean(KEY_MONITORAMENTO_ENABLED, isChecked);

            //Confirma a alteracao no arquivo
            editor.apply();

        // Fim da logica de persistencia

        });
    }

    private void setupLocalTimeoutReceiver() {
        timeoutMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothTimeoutReceiver.ACTION_BLUETOOTH_TIMEOUT.equals(intent.getAction())) {
                    String deviceName = intent.getStringExtra(BluetoothTimeoutReceiver.EXTRA_DEVICE_NAME);
                    String message = deviceName + " se desconectou por distância! (Timeout)";
                    statusTextView.setText(message);
                }
            }
        };
    }

    private void checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.i(TAG, "API < 31. Permissões legadas de Bluetooth assumidas.");
            registerBluetoothReceiver();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissão BLUETOOTH_CONNECT pendente. Solicitando...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT_PERMISSION);
        } else {
            Log.i(TAG, "Permissão BLUETOOTH_CONNECT já concedida.");
            registerBluetoothReceiver();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permissão BLUETOOTH_CONNECT concedida. Registrando Receiver.");
                registerBluetoothReceiver();

            } else {
                Log.e(TAG, "Permissão BLUETOOTH_CONNECT negada. Funcionalidade Bluetooth limitada.");
                Toast.makeText(this, "Permissão de Bluetooth negada. O monitoramento de timeout não funcionará.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try {
            registerReceiver(bluetoothTimeoutReceiver, filter);
            Log.i(TAG, "BluetoothTimeoutReceiver registrado com sucesso.");
        } catch (SecurityException e) {
            Log.e(TAG, "ERRO: Falha ao registrar o Receiver. Permissão ausente.", e);
        }
    }

    private void unregisterBluetoothReceiver() {
        if (bluetoothTimeoutReceiver != null) {
            try {
                unregisterReceiver(bluetoothTimeoutReceiver);
                Log.i(TAG, "BluetoothTimeoutReceiver desregistrado.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver já foi desregistrado ou nunca foi registrado.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(timeoutMessageReceiver, new IntentFilter(BluetoothTimeoutReceiver.ACTION_BLUETOOTH_TIMEOUT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timeoutMessageReceiver);
        unregisterBluetoothReceiver();
    }

}