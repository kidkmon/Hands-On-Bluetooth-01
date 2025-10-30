package com.example.bluetoothbroascasttestapp;

import android.Manifest;
import android.annotation.SuppressLint;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.bluetooth.btservice.AdapterNativeInterface;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.Utils;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothBroadcastApp";
    public static final String MONITORAMENTO_PREFS_NAME = "MonitoramentoPrefs";
    // Variavel com o valor booleano true/false
    public static final String MONITORAMENTO_KEY_ENABLED = "MonitoramentoKeyEnabled";

    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 101;
    private static final int HCI_REASON_CONNECTION_TIMEOUT = 0x08;
    private static final int JNI_ERROR_REASON = -1;

    private TextView statusTextView;
    private BroadcastReceiver aclReceiver;
    private AdapterNativeInterface mNativeInterface = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusTextView = findViewById(R.id.status_message);
        statusTextView.setText("Aguardando eventos...");

        try {
            AdapterService service = AdapterService.getAdapterService();
            if (service != null) {
                mNativeInterface = service.getNative();
                Log.i(TAG, "AdapterNativeInterface obtida via AdapterService.");
            } else {
                Log.e(TAG, "AdapterService é nulo. Acesso JNI pode falhar.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tentar obter AdapterNativeInterface: " + e.getMessage());
            Toast.makeText(this, "Erro ao acessar interface Bluetooth interna.", Toast.LENGTH_LONG).show();
            mNativeInterface = null;
        }

        setupAclReceiver();
        checkAndRequestBluetoothPermissions();
        setupSwitchButtons();
    }

    private void setupAclReceiver() {
        aclReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;

                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device == null) {
                        Log.e(TAG, "Dispositivo nulo no broadcast ACTION_ACL_DISCONNECTED.");
                        statusTextView.setText("Erro: Dispositivo nulo no evento.");
                        return;
                    }
                    if (mNativeInterface == null) {
                        Log.e(TAG, "Interface JNI não disponível para consultar o motivo.");
                        statusTextView.setText("Erro: Interface JNI indisponível.");
                        return;
                    }

                    String deviceAddress = device.getAddress();
                    Log.d(TAG, "Broadcast ACTION_ACL_DISCONNECTED recebido para: " + deviceAddress);

                    byte[] addressBytes = Utils.getBytesFromAddress(deviceAddress);
                    int reasonCode = JNI_ERROR_REASON;
                    try {
                        reasonCode = mNativeInterface.getDisconnectionReasonNative(addressBytes);
                        Log.i(TAG, "Motivo obtido via JNI para " + deviceAddress + ": " + reasonCode);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao chamar getDisconnectionReasonNative via JNI: " + e.getMessage());
                        statusTextView.setText("Erro ao chamar JNI para obter motivo.");
                        return;
                    }

                    String deviceName = "Dispositivo";
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            if(device.getName() != null) deviceName = device.getName();
                            else deviceName = deviceAddress;
                        } catch (SecurityException se) {
                            Log.w(TAG, "SecurityException ao obter nome, usando endereço MAC.", se);
                            deviceName = deviceAddress;
                        }
                    } else {
                        deviceName = deviceAddress;
                        Log.w(TAG, "Sem permissão BLUETOOTH_CONNECT para obter o nome.");
                    }

                    if (reasonCode == HCI_REASON_CONNECTION_TIMEOUT) {
                        String message = deviceName + " foi deixado para trás! (Timeout JNI)";
                        statusTextView.setText(message);
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        Log.w(TAG, message);
                    } else if (reasonCode != JNI_ERROR_REASON && reasonCode != 0) {
                        String message = deviceName + " desconectado. Motivo JNI: " + reasonCode;
                        statusTextView.setText(message);
                        Log.i(TAG, message);
                    } else if (reasonCode == 0) {
                        String message = deviceName + " desconectado normalmente.";
                        statusTextView.setText(message);
                        Log.i(TAG, message);
                    } else {
                        String message = deviceName + " desconectado. Motivo não pôde ser recuperado via JNI (código: " + reasonCode + ")";
                        statusTextView.setText(message);
                        Log.e(TAG, message);
                    }
                }
            }
        };
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerAclReceiver() {
        if (aclReceiver == null) {
            Log.e(TAG, "Receiver é nulo, não pode registrar.");
            return;
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        String requiredPermission = android.Manifest.permission.BLUETOOTH_CONNECT;
        Log.i(TAG, "Registrando AclReceiver para ACTION_ACL_DISCONNECTED...");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(aclReceiver, filter, requiredPermission, null, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(aclReceiver, filter, requiredPermission, null);
            }
            Log.i(TAG, "AclReceiver registrado com sucesso.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar AclReceiver: " + e.getMessage());
            Toast.makeText(this, "Erro ao registrar receiver Bluetooth.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aclReceiver != null) {
            try {
                unregisterReceiver(aclReceiver);
                Log.i(TAG, "AclReceiver desregistrado.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Tentativa de desregistrar Receiver que não estava registrado.");
            }
        }
    }

    private void checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Solicitando permissão BLUETOOTH_CONNECT...");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT_PERMISSION);
            } else {
                Log.i(TAG, "Permissão BLUETOOTH_CONNECT já concedida.");
                registerAclReceiver();
            }
        } else {
            Log.i(TAG, "API < 31. Permissões legadas assumidas via Manifest.");
            registerAclReceiver();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permissão BLUETOOTH_CONNECT concedida.");
                registerAclReceiver();
            } else {
                Log.e(TAG, "Permissão BLUETOOTH_CONNECT negada.");
                Toast.makeText(this, "Permissão de Bluetooth negada. O app não pode detectar desconexões.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupSwitchButtons() {
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch switchBluetooth = findViewById(R.id.switchBluetooth);

        if (switchBluetooth == null) {
            Log.e(TAG, "Erro: Não foi possível encontrar o Switch! Verifique o ID no XML.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(MONITORAMENTO_PREFS_NAME, Context.MODE_PRIVATE);
        boolean isSwitchEnabled = prefs.getBoolean(MONITORAMENTO_KEY_ENABLED, false);

        switchBluetooth.setChecked(isSwitchEnabled);

        switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                Log.d(TAG, "Monitoramento Inteligente: ATIVADO");
            } else {
                Log.d(TAG, "Monitoramento Inteligente: DESATIVADO");
            }

            SharedPreferences.Editor editor = getSharedPreferences(MONITORAMENTO_PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(MONITORAMENTO_KEY_ENABLED, isChecked);
            editor.apply();
        });
    }
}