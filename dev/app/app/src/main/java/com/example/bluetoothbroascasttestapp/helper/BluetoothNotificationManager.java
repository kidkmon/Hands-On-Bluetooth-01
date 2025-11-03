package com.example.bluetoothbroascasttestapp.helper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.bluetoothbroascasttestapp.R;

import static com.example.bluetoothbroascasttestapp.helper.Constant.CHANNEL_ID;

public class BluetoothNotificationManager {

    private static final int NOTIFICATION_ID = 1;

    public static void showNotification(Context context, String title, String text) {
        createNotificationChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
            if (notificationManager == null) return;

            // Verifica se o canal já existe
            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }

            String channelName = "Monitoramento RSSI";
            String channelDescription = "Canal para monitoramento de sinal Bluetooth RSSI";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, importance);
            channel.setDescription(channelDescription);
            notificationManager.createNotificationChannel(channel);
        }
    }
}