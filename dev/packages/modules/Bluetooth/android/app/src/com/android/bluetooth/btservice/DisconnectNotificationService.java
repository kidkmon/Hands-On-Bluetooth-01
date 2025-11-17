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

import android.provider.Settings; // app

/**
 * Service que roda no processo Bluetooth para monitorar desconexoes.
 * Ele ouve o broadcast ACTION_ACL_DISCONNECTED e, se a notificacao de timeout
 * estiver habilitada via prop, verifica o motivo da desconexao usando a JNI
 * (AdapterNativeInterface) e envia uma notificacao se for timeout (0x08).
 */
public class DisconnectNotificationService extends Service {

    private static final String TAG = "DisconnectNotificationService";
    private static final boolean DEBUG = true;

    public static final String ACTION_START_MONITORING = "com.android.bluetooth.btservice.action.START_MONITORING";

    // Constantes para a notificacao
    private static final String DISCONNECT_NOTIFY_CHANNEL_ID = "bluetooth_disconnect_timeout";
    private static final int DISCONNECT_NOTIFY_ID = 42; // ID unico para a notificacao
    
    private static final String FOREGROUND_CHANNEL_ID = "bluetooth_disconnect_service";
    private static final int FOREGROUND_NOTIFY_ID = 43; // notificacao silenciosa

    // Constante para o motivo de timeout HCI
    private static final int HCI_REASON_CONNECTION_TIMEOUT = 0x08; // mudado para 0x13 para fins de facilitar os testes, reason correto para timeout: 0x08

    // Propriedade do sistema para habilitar/desabilitar a feature via ADB
    // adb shell setprop persist.bluetooth.disconnect_notify.enabled true
    // private static final String PROP_DISCONNECT_NOTIFY_ENABLED = "persist.bluetooth.disconnect_notify.enabled";

    // habilitar afeature via app:
    private static final String SETTING_BLUETOOTH_TIMEOUT_NOTIFY = "breminder_bt_timeout_notify";


    // Valor padrao para RSSI invalido (igual ao da camada C++)
    private static final int RSSI_INVALID = -127;

    private NotificationManager mNotificationManager;
    private AdapterNativeInterface mNativeInterface;

    // private boolean mIsReceiverRegistered = false;

    private static DisconnectNotificationService sInstance = null;

    /*
     Retorna a instancia singleton do serviço.
     */
    public static DisconnectNotificationService getInstance() {
        if (sInstance == null) {
            Log.e(TAG, "SHAKKA_LOG: DisconnectNotificationService.getInstance() chamado, mas sInstance é nulo!");
        }
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SHAKKA_LOG: DisconnectNotificationService.onCreate() - INICIO");

        // Define a instancia do Singleton
        sInstance = this;

        // Obter a instancia singleton da sua interface JNI
        mNativeInterface = AdapterNativeInterface.getInstance();
        Log.i(TAG, "SHAKKA_LOG: ... Pegou mNativeInterface");
        
        // Obter o NotificationManager e criar o canal
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Log.i(TAG, "SHAKKA_LOG: ... Pegou NotificationManager");

        createNotificationChannel();
        Log.i(TAG, "SHAKKA_LOG: ... Criou canal de notificação");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_MONITORING.equals(intent.getAction())) {
            Log.i(TAG, "SHAKKA_LOG: onStartCommand - Ação: " + ACTION_START_MONITORING);
            Log.i(TAG, "SHAKKA_LOG: Serviço de monitoramento iniciado.");

            startSilentForeground();

        } else {
            Log.w(TAG, "SHAKKA_LOG: onStartCommand - Ação nula ou inesperada: " + (intent != null ? intent.getAction() : "null"));
        }
        
        // Queremos que este service continue rodando (para manter o receiver ativo)
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: DisconnectNotificationService.onDestroy()");
        
        // Limpa a instancia do Singleton
        sInstance = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*
     Este metodo e chamado pelo JniCallbacks
     quando um evento de desconexao ACL ocorre.
     */
    public void onAclDisconnected(byte[] address) {
        final String TAG = "DisconnectNotificationService"; // Para logs
        Log.i(TAG, "SHAKKA_LOG: [onAclDisconnected] Evento recebido!");

        // Verificar se a feature esta habilitada via prop
        // if (!SystemProperties.getBoolean(PROP_DISCONNECT_NOTIFY_ENABLED, false)) {
        //     Log.d(TAG, "SHAKKA_LOG: [onAclDisconnected] Notificação desabilitada via prop.");
        //     return;
        // }

        // verificar se a feature esta habilitada (lendo de Settings.Global)
        // O padrao e 1 (ATIVADO)
        boolean isEnabled = Settings.Global.getInt(getContentResolver(), 
                                SETTING_BLUETOOTH_TIMEOUT_NOTIFY, 1) == 1;

        if (!isEnabled) {
            Log.d(TAG, "SHAKKA_LOG: [onAclDisconnected] Notificação desabilitada via Settings.Global.");
            return;
        }

        // verificar se o motivo eh Connection Timeout
        int reason = mNativeInterface.getDisconnectionReasonNative(address);
        Log.i(TAG, "SHAKKA_LOG: [onAclDisconnected] Motivo buscado da JNI: 0x" + Integer.toHexString(reason));

        if (reason == HCI_REASON_CONNECTION_TIMEOUT) {
            BluetoothDevice device = AdapterService.getAdapterService().getDeviceFromByte(address);
            if (device == null) {
                Log.e(TAG, "SHAKKA_LOG: [onAclDisconnected] Dispositivo nulo, não é possível notificar.");
                return;
            }
            
            // CHAMAR JNI PARA BUSCAR O RSSI
            int rssi = mNativeInterface.getConnectedDeviceRssiNative(address); 
            
            Log.i(TAG, "SHAKKA_LOG: [onAclDisconnected] Desconexão por Timeout (0x08) detectada. Enviando notificação.");
            sendDisconnectNotification(device, rssi);
        }
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
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifica quando um dispositivo Bluetooth desconecta por falta de sinal (timeout).");
        channel.enableLights(true);
        channel.setLightColor(Color.BLUE);
        mNotificationManager.createNotificationChannel(channel);

        NotificationChannel foregroundChannel = new NotificationChannel(FOREGROUND_CHANNEL_ID,
                "Monitoramento Bluetooth", 
                NotificationManager.IMPORTANCE_MIN); // Importancia minima (sem som, sem pop-up)
        foregroundChannel.setDescription("Serviço que monitora a conexão Bluetooth para eventos de timeout.");
        mNotificationManager.createNotificationChannel(foregroundChannel);

        if (DEBUG) Log.d(TAG, "SHAKKA_LOG: Canal de notificação criado.");
    }

    private void startSilentForeground() {
        if (mNotificationManager == null) {
            Log.e(TAG, "SHAKKA_LOG: NotificationManager nulo, não é possível enviar notificação 'Teste'.");
            return;
        }

        String title = "Monitoramento Bluetooth";
        String content = "Monitorando ativamente a conexão Bluetooth.";
        int icon = android.R.drawable.stat_sys_data_bluetooth;

        Notification notification = new Notification.Builder(this, FOREGROUND_CHANNEL_ID) 
            .setSmallIcon(icon) 
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .build();

        try {
            startForeground(FOREGROUND_NOTIFY_ID, notification); 
            Log.i(TAG, "SHAKKA_LOG: Serviço promovido para Foreground (Silencioso).");
        } catch (Exception e) {
            Log.e(TAG, "SHAKKA_LOG: FALHA ao chamar startForeground()", e);
        }
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
        int icon = android.R.drawable.stat_sys_data_bluetooth;

        Notification notification = new Notification.Builder(this, DISCONNECT_NOTIFY_CHANNEL_ID)
                .setSmallIcon(icon) 
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content)) // Permite texto longo
                .setAutoCancel(true)
                .build();

        try {
            // Use o ID de notificacao correto (o 42)
            mNotificationManager.notify(DISCONNECT_NOTIFY_ID, notification); 
            Log.i(TAG, "SHAKKA_LOG: Notificação de TIMEOUT enviada para " + deviceName);
        } catch (Exception e) {
            Log.e(TAG, "SHAKKA_LOG: FALHA ao chamar mNotificationManager.notify()", e);
        }
    }
}