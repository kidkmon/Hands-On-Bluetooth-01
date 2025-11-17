package com.android.breminder.settings;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.util.Log;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DisconnectNotifierSettings";

    // A "chave" global para nossa configuracao.
    // Usamos 1 como padrao (ativado).
    public static final String SETTING_BLUETOOTH_TIMEOUT_NOTIFY = "breminder_bt_timeout_notify";

    private SwitchMaterial mTimeoutSwitch;
    private SwitchMaterial mSafeZoneSwitch; // Placeholder

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTimeoutSwitch = findViewById(R.id.switch_timeout_notifier);
        mSafeZoneSwitch = findViewById(R.id.switch_safe_zone_wifi);
        
        // Carrega o estado atual da configuracao ao abrir o app
        loadSettings();

        // Configura o listener (ouvinte) para o botao
        mTimeoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Salva a nova configuracao quando o usuario tocar no toggle
                saveSetting(isChecked);
            }
        });

        // (O boyao da Zona Segura nao faz nada por enquanto)
        mSafeZoneSwitch.setEnabled(false);
    }

    /**
     * Carrega o valor atual do Settings.Global e atualiza o toggle.
     */
    private void loadSettings() {
        boolean isEnabled = Settings.Global.getInt(getContentResolver(), 
                                SETTING_BLUETOOTH_TIMEOUT_NOTIFY, 1) == 1; // 1 = Padrao ATIVADO
        
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
            // aontecera se o app nao for um priv-app
            // ou se a permissao WRITE_SECURE_SETTINGS estiver faltando.
            Log.e(TAG, "FALHA AO SALVAR CONFIGURAÇÃO! Verifique as permissões de priv-app.", e);
        }
    }
}