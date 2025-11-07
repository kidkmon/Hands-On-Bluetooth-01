/*
 * 2025 DevTitans Project
 * Author: Matheus Fernandes Oliveira
 */

package com.android.bluetooth.btservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import com.android.bluetooth.Utils;
import android.Manifest;
import com.android.bluetooth.R; 

/**
 * Service que roda no processo Bluetooth para monitorar desconexoes.
 * Ele ouve o broadcast ACTION_ACL_DISCONNECTED e, se a notificacao de timeout
 * estiver habilitada via prop, verifica o motivo da desconexao usando a JNI
 * (AdapterNativeInterface) e envia uma notificacao se for timeout (0x08).
 */
public class DisconnectNotificationService extends Service {

    private static final String TAG = "DisconnectNotificationService";
    private static final boolean DEBUG = true;

    // Constantes para a notificacao
    private static final String DISCONNECT_NOTIFY_CHANNEL_ID = "bluetooth_disconnect_timeout";
    private static final int DISCONNECT_NOTIFY_ID = 42; // ID unico para a notificacao

    // Constante para o motivo de timeout HCI
    private static final int HCI_REASON_CONNECTION_TIMEOUT = 0x08;

    // Propriedade do sistema para habilitar/desabilitar a feature via ADB
    // adb shell setprop persist.bluetooth.disconnect_notify.enabled true
    private static final String PROP_DISCONNECT_NOTIFY_ENABLED = "persist.bluetooth.disconnect_notify.enabled";

    // Valor padrao para RSSI invalido (igual ao da camada C++)
    private static final int RSSI_INVALID = -127;

    private NotificationManager mNotificationManager;
    private AdapterNativeInterface mNativeInterface;

    // Receiver para ouvir desconexoes ACL
    private final BroadcastReceiver mDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null || !action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                return;
            }

            // verifica se a feature esta habilitada via ADB
            if (!SystemProperties.getBoolean(PROP_DISCONNECT_NOTIFY_ENABLED, false)) {
                if (DEBUG) Log.d(TAG, "ACL disconnect recebido, mas a notificação está desabilitada.");
                return;
            }

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                if (DEBUG) Log.w(TAG, "Broadcast de desconexão sem BluetoothDevice extra.");
                return;
            }

            byte[] address = Utils.getByteAddress(device);
            if (mNativeInterface == null) {
                 Log.e(TAG, "mNativeInterface é nulo, não é possível obter o motivo da desconexão.");
                 return;
            }
            
            // chama as funcoes JNI
            int reason = mNativeInterface.getDisconnectionReasonNative(address);
            int rssi = mNativeInterface.getConnectedDeviceRssiNative(address); 

            if (DEBUG) {
                Log.d(TAG, "SHAKKA_LOG: DisconnectReceiver - Dispositivo: " + device.getAddress() 
                           + ", Motivo: 0x" + Integer.toHexString(reason)
                           + ", Último RSSI: " + rssi);
            }

            // verifica se o motivo eh Connection Timeout
            if (reason == HCI_REASON_CONNECTION_TIMEOUT) {
                Log.i(TAG, "SHAKKA_LOG: Desconexão por Timeout (0x08) detectada para " + device.getAddress());
                sendDisconnectNotification(device, rssi);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: DisconnectNotificationService.onCreate()");

        // Obter a instancia singleton da sua interface JNI
        mNativeInterface = AdapterNativeInterface.getInstance();
        
        // Obter o NotificationManager e criar o canal
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // Registrar o BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mDisconnectReceiver, filter,
                Manifest.permission.BLUETOOTH_CONNECT,
                null, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: DisconnectNotificationService.onStartCommand()");
        
        // Queremos que este servico continue rodando.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: DisconnectNotificationService.onDestroy()");
        
        // Limpar o receiver
        unregisterReceiver(mDisconnectReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Cria o canal de notificacao
     */
    private void createNotificationChannel() {
        if (mNotificationManager == null) {
            Log.e(TAG, "SHAKKA_LOG: NotificationManager não disponível.");
            return;
        }
        
        NotificationChannel channel = new NotificationChannel(DISCONNECT_NOTIFY_CHANNEL_ID,
                "Desconexões por Timeout",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notifica quando um dispositivo Bluetooth desconecta por falta de sinal (timeout).");
        channel.enableLights(true);
        channel.setLightColor(Color.BLUE);
        mNotificationManager.createNotificationChannel(channel);
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: Canal de notificação criado.");
    }

    /**
     * Constroi e envia a notificacao de desconexao.
     */
    private void sendDisconnectNotification(BluetoothDevice device, int rssi) {
        if (mNotificationManager == null) {
            Log.e(TAG, "SHAKKA_LOG: NotificationManager nulo, não é possível enviar notificação.");
            return;
        }

        String deviceName = device.getName();
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = device.getAddress(); // Fallback para o endereco MAC
        }

        String title = "Dispositivo Bluetooth Desconectado";
        String content = "O dispositivo '" + deviceName + "' desconectou por timeout (sem sinal).";
        
        // Adiciona o RSSI se for valido (diferente de -127)
        if (rssi != RSSI_INVALID) { 
            content += " O último sinal recebido era de " + rssi + " dBm.";
        }

        // Tenta usar o icone do app Bluetooth, senao usa o icone padrao do sistema
        int icon = R.mipmap.bt_share; // Do com.android.bluetooth.R
        if (icon == 0) {
             icon = android.R.drawable.stat_sys_data_bluetooth; // Fallback
        }


        Notification notification = new Notification.Builder(this, DISCONNECT_NOTIFY_CHANNEL_ID)
                .setSmallIcon(icon) 
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content)) // Permite texto longo
                .setAutoCancel(true)
                .build();

        mNotificationManager.notify(DISCONNECT_NOTIFY_ID, notification);
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: Notificação enviada para " + deviceName);
    }
}