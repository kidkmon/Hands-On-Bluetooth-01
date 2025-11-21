/*
 * Copyright (C) 2012-2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.btservice;

import android.bluetooth.OobData;
import android.bluetooth.UidTraffic;
import android.util.Log;
import com.android.bluetooth.flags.Flags;

class JniCallbacks {

    private RemoteDevices mRemoteDevices;
    private AdapterProperties mAdapterProperties;
    private AdapterService mAdapterService;
    private BondStateMachine mBondStateMachine;

    JniCallbacks(AdapterService adapterService, AdapterProperties adapterProperties) {
        mAdapterService = adapterService;
        mAdapterProperties = adapterProperties;
    }

    void init(BondStateMachine bondStateMachine, RemoteDevices remoteDevices) {
        mRemoteDevices = remoteDevices;
        mBondStateMachine = bondStateMachine;
    }

    void cleanup() {
        mRemoteDevices = null;
        mAdapterProperties = null;
        mAdapterService = null;
        mBondStateMachine = null;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    void sspRequestCallback(byte[] address, byte[] name, int cod, int pairingVariant, int passkey) {
        mBondStateMachine.sspRequestCallback(address, name, cod, pairingVariant, passkey);
    }

    void devicePropertyChangedCallback(byte[] address, int[] types, byte[][] val) {
        mRemoteDevices.devicePropertyChangedCallback(address, types, val);
    }

    void deviceFoundCallback(byte[] address) {
        mRemoteDevices.deviceFoundCallback(address);
    }

    void pinRequestCallback(byte[] address, byte[] name, int cod, boolean min16Digits) {
        mBondStateMachine.pinRequestCallback(address, name, cod, min16Digits);
    }

    void bondStateChangeCallback(int status, byte[] address, int newState, int hciReason) {
        mBondStateMachine.bondStateChangeCallback(status, address, newState, hciReason);
        if (Flags.removeBondWithAddressMap()) {
            mRemoteDevices.onBondStateChange(address, newState);
        }
    }

    void addressConsolidateCallback(byte[] mainAddress, byte[] secondaryAddress) {
        mRemoteDevices.addressConsolidateCallback(mainAddress, secondaryAddress);
    }

    void leAddressAssociateCallback(byte[] mainAddress, byte[] secondaryAddress) {
        mRemoteDevices.leAddressAssociateCallback(mainAddress, secondaryAddress);
    }

    public void aclStateChangeCallback(int status, byte[] address, int newState,
            int transportLinkType, int hciReason, int handle) {

        Log.i("JniCallbacks", "SHAKKA_LOG: aclStateChangeCallback!! newState=" + newState 
                + ", hciReason=0x" + Integer.toHexString(hciReason));
        
        // Se nao for o nosso estado especial, passa para o sistema normal
        if (newState != 3) {
             mRemoteDevices.aclStateChangeCallback(status, address, newState,
                transportLinkType, hciReason, handle);
        }

        // ***** LOGICA DE ALERTA DE DISTANCIA *****
        // newState 3 = Nosso codigo para "Alerta de RSSI Baixo"
        if (newState == 3) {
             Log.i("JniCallbacks", "SHAKKA_LOG: Recebido estado 3 (Alerta de RSSI)!");
             AdapterService service = AdapterService.getAdapterService();
             if (service != null) {
                 service.handleRssiAlert(address, -35); // Valor aproximado
             }
             return; // Nao faz mais nada
        }

        // state 0 == AbstractionLayer.BT_ACL_STATE_DISCONNECTED
        // Verificamos se o estado e "desconectado"
        if (newState != 0) { 
            // Pega a insancia Singleton do seu servico
            DisconnectNotificationService service = DisconnectNotificationService.getInstance();
            
            if (service != null) {
                // Chama novo metodo
                // Isso aciona o servico, que entao usara a JNI para buscar o motivo.
                Log.i("JniCallbacks", "SHAKKA_LOG: newState=0 e hciReason!=0. ACIONANDO onAclDisconnected!");
                service.onAclDisconnected(address);
            } else {
                Log.e("JniCallbacks", "SHAKKA_LOG: aclStateChangeCallback (DISCONNECTED) mas DisconnectNotificationService.getInstance() é nulo!");
            }
        }
    }

    void keyMissingCallback(byte[] address) {
        mRemoteDevices.keyMissingCallback(address);
    }

    void stateChangeCallback(int status) {
        mAdapterService.stateChangeCallback(status);
    }

    void discoveryStateChangeCallback(int state) {
        mAdapterProperties.discoveryStateChangeCallback(state);
    }

    void adapterPropertyChangedCallback(int[] types, byte[][] val) {
        mAdapterProperties.adapterPropertyChangedCallback(types, val);
    }

    void oobDataReceivedCallback(int transport, OobData oobData) {
        mAdapterService.notifyOobDataCallback(transport, oobData);
    }

    void linkQualityReportCallback(
            long timestamp,
            int report_id,
            int rssi,
            int snr,
            int retransmission_count,
            int packets_not_receive_count,
            int negative_acknowledgement_count) {
        mAdapterService.linkQualityReportCallback(
                timestamp, report_id, rssi, snr, retransmission_count,
                packets_not_receive_count, negative_acknowledgement_count);
    }

    void switchBufferSizeCallback(boolean is_low_latency_buffer_size) {
        mAdapterService.switchBufferSizeCallback(is_low_latency_buffer_size);
    }

    void switchCodecCallback(boolean is_low_latency_buffer_size) {
        mAdapterService.switchCodecCallback(is_low_latency_buffer_size);
    }

    boolean acquireWakeLock(String lockName) {
        return mAdapterService.acquireWakeLock(lockName);
    }

    boolean releaseWakeLock(String lockName) {
        return mAdapterService.releaseWakeLock(lockName);
    }

    void energyInfoCallback(
            int status,
            int ctrlState,
            long txTime,
            long rxTime,
            long idleTime,
            long energyUsed,
            UidTraffic[] data) {
        mAdapterService.energyInfoCallback(
                status, ctrlState, txTime, rxTime, idleTime, energyUsed, data);
    }
}
