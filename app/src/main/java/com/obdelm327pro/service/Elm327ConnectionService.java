package com.obdelm327pro.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.obdelm327pro.BluetoothService;
import com.obdelm327pro.MainActivity;
import com.obdelm327pro.ObdWifiManager;
import com.obdelm327pro.R;
import com.obdelm327pro.TroubleCodes;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.os.Build.VERSION_CODES.O;
import static com.obdelm327pro.request.RequestCode.ACTIVITY_REQUEST_CODE;
import static com.obdelm327pro.service.Message.MESSAGE_DEVICE_NAME;
import static com.obdelm327pro.service.Message.MESSAGE_READ;
import static com.obdelm327pro.service.Message.MESSAGE_STATE_CHANGE;
import static com.obdelm327pro.service.Message.MESSAGE_TOAST;
import static com.obdelm327pro.service.Message.MESSAGE_WRITE;
import static com.obdelm327pro.service.Utils.isHexadecimal;

public class Elm327ConnectionService extends Service {

    private static final String TAG = "Elm327ConnectionService";

    public static final String DISCONNECT_CONNECTION_SERVICE = "com.obdelm327pro.service.Elm327ConnectionService.disconnectConnection";

    private static final int SUMMARY_NOTIFICATION_ID = 1;
    private static final int RPM_NOTIFICATION_ID = 3;
    private static final int MAF_FLOW_NOTIFICATION_ID = 4;
    private static final int AIR_TEMP_NOTIFICATION_ID = 5;
    private static final int VOLTAGE_NOTIFICATION_ID = 6;
    private static final int AGGRESSIVE_DRIVING_NOTIFICATION_ID = 7;
    private static final int FUEL_CONSUMPTION_LOW_NOTIFICATION_ID = 8;
    private static final int COOLANT_AIR_TEMP_HIGH_NOTIFICATION_ID = 9;

    private static final int DEFAULT_NOTIFICATION_ID = 101;

    private String NOTIFICATION_CHANNEL_ID;


    private Elm327ConnectionBinder binder = new MyElm327ConnectionBinder();

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    protected final static char[] dtcLetters = {'P', 'C', 'B', 'U'};
    protected final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static final String[] PIDS = {
            "01", "02", "03", "04", "05", "06", "07", "08",
            "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
            "11", "12", "13", "14", "15", "16", "17", "18",
            "19", "1A", "1B", "1C", "1D", "1E", "1F", "20"};

    final List<String> commandsList = new ArrayList<>();

    final List<Double> avgConsumption = new ArrayList<>();
    final List<String> troubleCodesArray = new ArrayList<>();

    BluetoothDevice currentDevice;
    boolean commandMode = false, initialized = false, m_getPIDs = false, tryConnect = false;

    private DeviceInfo deviceInfo;
//    String deviceName = null, deviceProtocol = null;

    //ATZ reset all
    //ATDP Describe the current Protocol
    //ATAT0-1-2 Adaptive Timing Off - daptive Timing Auto1 - daptive Timing Auto2
    //ATE0-1 Echo Off - Echo On
    //ATSP0 Set Protocol to Auto and save it
    //ATMA Monitor All
    //ATL1-0 Linefeeds On - Linefeeds Off
    //ATH1-0 Headers On - Headers Off
    //ATS1-0 printing of Spaces On - printing of Spaces Off
    //ATAL Allow Long (>7 byte) messages
    //ATRD Read the stored data
    //ATSTFF Set time out to maximum
    //ATSTHH Set timeout to 4ms
    String[] initializeCommands = new String[]{"ATZ", "ATL0", "ATE1", "ATH1", "ATAT1", "ATSTFF", "ATI", "ATDP", "ATSP0", "ATSP0"};
    TroubleCodes troubleCodes;
    String VOLTAGE = "ATRV",
            PROTOCOL = "ATDP",
            RESET = "ATZ",
            ENGINE_COOLANT_TEMP = "0105",  //A-40
            ENGINE_RPM = "010C",  //((A*256)+B)/4
            ENGINE_LOAD = "0104",  // A*100/255
            VEHICLE_SPEED = "010D",  //A
            INTAKE_AIR_TEMP = "010F",  //A-40
            MAF_AIR_FLOW = "0110", //MAF air flow rate 0 - 655.35	grams/sec ((256*A)+B) / 100  [g/s]
            ENGINE_OIL_TEMP = "015C",  //A-40
            FUEL_RAIL_PRESSURE = "0122", // ((A*256)+B)*0.079
            INTAKE_MAN_PRESSURE = "010B", //Intake manifold absolute pressure 0 - 255 kPa
            CONT_MODULE_VOLT = "0142",  //((A*256)+B)/1000
            AMBIENT_AIR_TEMP = "0146",  //A-40
            CATALYST_TEMP_B1S1 = "013C",  //(((A*256)+B)/10)-40
            STATUS_DTC = "0101", //Status since DTC Cleared
            THROTTLE_POSITION = "0111", //Throttle position 0 -100 % A*100/255
            OBD_STANDARDS = "011C", //OBD standards this vehicle
            PIDS_SUPPORTED = "0120"; //PIDs supported

    private PowerManager.WakeLock wl;

    private String mConnectedDeviceName = "Ecu";
    private SpeedAtTime lastSpeedAtTime;
    private int whichCommand = 0, mDetect_PIDs = 0, connectCount = 0, tryCount = 0;
    private AirflowRate massAirflow;
    private int mEngineDisplacement = 1500;

    private List<WeakReference<Elm327Callback>> mElm327Callbacks = null;

    private List<WeakReference<BluetoothODBCallback>> mBluetoothOdbCallbacks = null;
    // Member object for the chat services
    private BluetoothService mBtService = null;

    private List<WeakReference<WifiODBCallback>> mWifiOdbCallbacks = null;
    private ObdWifiManager mWifiService = null;


    private void notifyDeviceInfoUpdate(@NonNull DeviceInfo deviceInfo) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onDeviceInfo(deviceInfo);
            }
        }
    }

    private void notifyInfoUpdate(@NonNull String info) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onInfo(info);
            }
        }
    }

    private void notifyNewConversation(@NonNull String conversation) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onNewConversation(conversation);
            }
        }
    }

    private void notifyToastMessage(@NonNull String message) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().toastMessage(message);
            }
        }
    }

    private void notifyVoltageUpdate(@NonNull Voltage voltage) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onVoltageUpdate(voltage);
            }
        }
    }

    private void notifyEngineLoadUpdate(@NonNull EngineLoad engineLoad) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onEngineLoadUpdate(engineLoad);
            }
        }
    }

    private void notifyFuelConsumptionUpdate(@NonNull FuelConsumption fuelConsumption) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onFuelConsumptionUpdate(fuelConsumption);
            }
        }
    }

    private void notifyCoolantTemperatureUpdate(@NonNull Temperature coolantTemp) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onCoolantTemperatureUpdate(coolantTemp);
            }
        }
    }

    private void notifyRpmUpdate(@NonNull RPM rpm) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onRpmUpdate(rpm);
            }
        }
    }

    private void notifySpeedUpdate(Speed speed) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onSpeedUpdate(speed);
            }
        }
    }

    private void notifyIntakeTempUpdate(@NonNull Temperature intakeTempUpdate) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onIntakeTemperatureUpdate(intakeTempUpdate);
            }
        }
    }

    private void notifyMAF_AirFlowUpdate(@NonNull AirflowRate massAirflow) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onMAF_AirFlowUpdate(massAirflow);
            }
        }
    }

    private void notifyThrottlePositionUpdate(@NonNull ThrottlePosition throttlePosition) {
        for (int i = 0; i < mElm327Callbacks.size(); i++) {
            WeakReference<Elm327Callback> reference = mElm327Callbacks.get(i);

            if (reference.get() != null) {
                reference.get().onThrottlePositionUpdate(throttlePosition);
            }
        }
    }

    private void notifyWifiStateChanged(@NonNull WifiState state) {
        for (int i = 0; i < mWifiOdbCallbacks.size(); i++) {
            WeakReference<WifiODBCallback> reference = mWifiOdbCallbacks.get(i);

            if (reference.get() != null) {
                reference.get().onStateChanged(state);
            }
        }
    }

    private static class WifiHandler extends Handler {

        private WeakReference<Elm327ConnectionService> mService;

        public WifiHandler(Elm327ConnectionService service) {
            mService = new WeakReference<>(service);
        }

        private Elm327ConnectionService getService() {
            return mService.get();
        }

        @Override
        public void handleMessage(android.os.Message msg) {

            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {
                        case ObdWifiManager.STATE_CONNECTED:
                            getService().updateDefaultNotificationContent(getService().getString(R.string.notification_content_connected, getService().getString(R.string.wifi)));
                            getService().notifyWifiStateChanged(WifiState.STATE_CONNECTED);
                            break;
                        case ObdWifiManager.STATE_CONNECTING:
                            getService().notifyWifiStateChanged(WifiState.STATE_CONNECTING);
                            break;
                        case ObdWifiManager.STATE_NONE:
                            if (getService().mWifiService != null) {
                                getService().mWifiService.disconnect();
                            }
                            getService().mWifiService = null;

                            getService().notifyWifiStateChanged(WifiState.STATE_NONE);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:

                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);

                    if (getService().commandMode || !getService().initialized) {
                        getService().notifyNewConversation("Command:  " + writeMessage);
                    }

                    break;

                case MESSAGE_READ:

                    String tmpmsg = getService().clearMsg(msg);

                    getService().notifyInfoUpdate(tmpmsg);

                    if (tmpmsg.contains(RSP_ID.NODATA.response) || tmpmsg.contains(RSP_ID.ERROR.response)) {

                        try{
                            String command = tmpmsg.substring(0,4);

                            if(isHexadecimal(command))
                            {
                                getService().removePID(command);
                            }

                        }
                        catch(Exception e)
                        {
//                            Toast.makeText(getService().getApplicationContext(), e.getMessage(),
//                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    if (getService().commandMode || !getService().initialized) {
                        getService().notifyNewConversation(getService().mConnectedDeviceName + ":  " + tmpmsg);
                    }

                    getService().analyzeMsg(msg);
                    break;

                case MESSAGE_DEVICE_NAME:
                    getService().mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    break;

                case MESSAGE_TOAST:
                    String toastMessage = msg.getData().getString(TOAST);
                    if (toastMessage != null) {
                        getService().notifyToastMessage(toastMessage);
                    }
                    break;
            }
        }
    }


    private final WifiHandler mWifiHandler = new WifiHandler(this);

    private void notifyBluetoothStateChanged(@NonNull BluetoothState state) {
        for (int i = 0; i < mBluetoothOdbCallbacks.size(); i++) {
            WeakReference<BluetoothODBCallback> reference = mBluetoothOdbCallbacks.get(i);

            if (reference.get() != null) {
                reference.get().onStateChanged(state);
            }
        }
    }

    private static class BluetoothHandler extends Handler {
        private WeakReference<Elm327ConnectionService> mService;


        public BluetoothHandler(Elm327ConnectionService service) {
            mService = new WeakReference<>(service);
        }

        private Elm327ConnectionService getService() {
            return mService.get();
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:

                            getService().updateDefaultNotificationContent(getService().getString(R.string.notification_content_connected, getService().getString(R.string.bluetooth)));

                            getService().notifyBluetoothStateChanged(BluetoothState.STATE_CONNECTED);

                            getService().tryConnect = false;

                            try {
                                getService().sendEcuMessage(getService().RESET);
                            }
                            catch (BluetoothConnectionNotEstablished ignored) {

                            }

                            break;
                        case BluetoothService.STATE_CONNECTING:
                            getService().notifyBluetoothStateChanged(BluetoothState.STATE_CONNECTING);
                            break;
                        case BluetoothService.STATE_LISTEN:
                            getService().notifyBluetoothStateChanged(BluetoothState.STATE_LISTEN);

                            if (getService().tryConnect) {
                                getService().mBtService.connect(getService().currentDevice);
                                getService().connectCount++;
                                if (getService().connectCount >= 2) {
                                    getService().tryConnect = false;
                                }
                            }

                            break;

                        case BluetoothService.STATE_NONE:

                            getService().notifyBluetoothStateChanged(BluetoothState.STATE_NONE);

                            if (getService().tryConnect) {
                                getService().mBtService.connect(getService().currentDevice);
                                getService().connectCount++;
                                if (getService().connectCount >= 2) {
                                    getService().tryConnect = false;
                                }
                            }

                            break;
                    }
                    break;
                case MESSAGE_WRITE:

                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);

                    if (getService().commandMode || !getService().initialized) {
                        getService().notifyNewConversation("Command:  " + writeMessage);
                    }

                    break;
                case MESSAGE_READ:

                    String tmpmsg = getService().clearMsg(msg);

                    getService().notifyInfoUpdate(tmpmsg);

                    if (getService().commandMode || !getService().initialized) {
                        getService().notifyNewConversation(getService().mConnectedDeviceName + ":  " + tmpmsg);
                    }

                    getService().analyzeMsg(msg);

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    getService().mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    break;
                case MESSAGE_TOAST:
                    String toastMsg = msg.getData().getString(TOAST);
                    if (toastMsg != null) {
                        getService().notifyToastMessage(toastMsg);
                    }
                    break;
            }
        }
    }

    private final Handler mBtHandler = new BluetoothHandler(this);

    private void removePID(String pid)
    {
        int index = commandsList.indexOf(pid);

        if (index != -1)
        {
            String info = "Removed pid: " + pid;
            notifyInfoUpdate(info);
        }
    }

    private String clearMsg(Message msg) {
        String tmpmsg = msg.obj.toString();

        tmpmsg = tmpmsg.replace("null", "");
        tmpmsg = tmpmsg.replaceAll("\\s", ""); //removes all [ \t\n\x0B\f\r]
        tmpmsg = tmpmsg.replaceAll(">", "");
        tmpmsg = tmpmsg.replaceAll("SEARCHING...", "");
        tmpmsg = tmpmsg.replaceAll("ATZ", "");
        tmpmsg = tmpmsg.replaceAll("ATI", "");
        tmpmsg = tmpmsg.replaceAll("atz", "");
        tmpmsg = tmpmsg.replaceAll("ati", "");
        tmpmsg = tmpmsg.replaceAll("ATDP", "");
        tmpmsg = tmpmsg.replaceAll("atdp", "");
        tmpmsg = tmpmsg.replaceAll("ATRV", "");
        tmpmsg = tmpmsg.replaceAll("atrv", "");

        return tmpmsg;
    }

    private void generateVolt(String msg) {

        String VoltText = null;

        if ((msg != null) && (msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})\\s*"))) {

            VoltText = msg + "V";

            notifyNewConversation(mConnectedDeviceName + ": " + msg + "V");

        } else if ((msg != null) && (msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})?V\\s*"))) {

            VoltText = msg;

            notifyNewConversation(mConnectedDeviceName + ": " + msg);
        }

        if (VoltText != null) {
            Log.d(TAG, "Voltage: " + VoltText);
            double voltage = Double.parseDouble(VoltText.substring(0, VoltText.indexOf('V')));
            if (voltage < 12) {
                sendNotification(VOLTAGE_NOTIFICATION_ID, "Warning! Your car battery voltage is low.",
                        "Current voltage is '" + VoltText + "'.");
            }
            notifyVoltageUpdate(new Voltage(voltage));
        }
    }

    private void getElmInfo(String tmpmsg) {

        String deviceName = null, deviceProtocol = null;
        if (tmpmsg.contains("ELM") || tmpmsg.contains("elm")) {
            deviceName = tmpmsg;
        }

        if (tmpmsg.contains("SAE") || tmpmsg.contains("ISO")
                || tmpmsg.contains("sae") || tmpmsg.contains("iso") || tmpmsg.contains("AUTO")) {
            deviceProtocol = tmpmsg;
        }

        if (deviceProtocol != null && deviceName != null) {
            deviceName = deviceName.replaceAll("STOPPED", "");
            deviceProtocol = deviceProtocol.replaceAll("STOPPED", "");

            deviceInfo = new DeviceInfo(deviceName, deviceProtocol);
            notifyDeviceInfoUpdate(deviceInfo);
        }
    }

    private void sendEcuMessage(String message) throws BluetoothConnectionNotEstablished {

        if(isWifiConnected())
        {
            try {
                if (message.length() > 0) {
                    message = message + "\r";
                    byte[] send = message.getBytes();
                    mWifiService.write(send);
                }
            } catch (Exception ignored) {
            }
        }

        // Check that we're actually connected before trying anything
        if (!isBluetoothConnected()) {
            throw new BluetoothConnectionNotEstablished();
        }

        if (mBtService != null) {
            try {
                if (message.length() > 0) {

                    message = message + "\r";
                    // Get the message bytes and tell the BluetoothChatService to write
                    byte[] send = message.getBytes();
                    mBtService.write(send);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void loadDefaultCommands() {

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());

        String engineDisplacement = preferences.getString("EngineDisplacement", "1500");

        if (engineDisplacement != null) {
            mEngineDisplacement = Integer.parseInt(engineDisplacement);
        }

        String detectPids = preferences.getString("DetectPids", "0");

        if (detectPids != null) {
            mDetect_PIDs = Integer.parseInt(detectPids);
        }

        if (mDetect_PIDs == 0) {

            commandsList.clear();

            int i = 0;

            commandsList.add(i, VOLTAGE);

            if (preferences.getBoolean("checkboxENGINE_RPM", true)) {
                commandsList.add(i, ENGINE_RPM);
                i++;
            }

            commandsList.add(i++, AMBIENT_AIR_TEMP);
            commandsList.add(i++, ENGINE_OIL_TEMP);

            if (preferences.getBoolean("checkboxVEHICLE_SPEED", true)) {
                commandsList.add(i, VEHICLE_SPEED);
                i++;
            }

            if (preferences.getBoolean("checkboxENGINE_LOAD", true)) {
                commandsList.add(i, ENGINE_LOAD);
                i++;
            }

            if (preferences.getBoolean("checkboxENGINE_COOLANT_TEMP", true)) {
                commandsList.add(i, ENGINE_COOLANT_TEMP);
                i++;
            }

            if (preferences.getBoolean("checkboxINTAKE_AIR_TEMP", true)) {
                commandsList.add(i, INTAKE_AIR_TEMP);
                i++;
            }

            if (preferences.getBoolean("checkboxMAF_AIR_FLOW", true)) {
                commandsList.add(i, MAF_AIR_FLOW);
            }

            whichCommand = 0;
        }
    }

    private void sendDefaultCommands() {

        if (commandsList.size() != 0) {

            if (whichCommand < 0) {
                whichCommand = 0;
            }

            String send = commandsList.get(whichCommand);
            try {
                sendEcuMessage(send);
            }
            catch (BluetoothConnectionNotEstablished ignored) {

            }

            if (whichCommand >= commandsList.size() - 1) {
                whichCommand = 0;
            } else {
                whichCommand++;
            }
        }
    }

    private void sendInitCommands() {
        if (initializeCommands.length != 0) {

            if (whichCommand < 0) {
                whichCommand = 0;
            }

            String send = initializeCommands[whichCommand];
            try {
                sendEcuMessage(send);
            }
            catch (BluetoothConnectionNotEstablished ignored) {

            }

            if (whichCommand == initializeCommands.length - 1) {
                initialized = true;
                whichCommand = 0;
                sendDefaultCommands();
            } else {
                whichCommand++;
            }
        }
    }

    private void setPIDsSupported(String buffer) {

        notifyInfoUpdate("Trying to get available PIDs : " + tryCount);
        tryCount++;

        StringBuilder flags = new StringBuilder();
        String buf = buffer;
        buf = buf.trim();
        buf = buf.replace("\t", "");
        buf = buf.replace(" ", "");
        buf = buf.replace(">", "");

        if (buf.indexOf("4100") == 0 || buf.indexOf("4120") == 0) {

            for (int i = 0; i < 8; i++) {
                String tmp = buf.substring(i + 4, i + 5);
                int data = Integer.valueOf(tmp, 16);
//                String retStr = Integer.toBinaryString(data);
                if ((data & 0x08) == 0x08) {
                    flags.append("1");
                } else {
                    flags.append("0");
                }

                if ((data & 0x04) == 0x04) {
                    flags.append("1");
                } else {
                    flags.append("0");
                }

                if ((data & 0x02) == 0x02) {
                    flags.append("1");
                } else {
                    flags.append("0");
                }

                if ((data & 0x01) == 0x01) {
                    flags.append("1");
                } else {
                    flags.append("0");
                }
            }

            commandsList.clear();
            commandsList.add(0, VOLTAGE);
            int pid = 1;

            StringBuilder supportedPID = new StringBuilder();
            supportedPID.append("Supported PIDs:\n");
            for (int j = 0; j < flags.length(); j++) {
                if (flags.charAt(j) == '1') {
                    supportedPID.append(" ").append(PIDS[j]).append(" ");
                    if (!PIDS[j].contains("11") && !PIDS[j].contains("01") && !PIDS[j].contains("20")) {
                        commandsList.add(pid, "01" + PIDS[j]);
                        pid++;
                    }
                }
            }
            m_getPIDs = true;
            notifyNewConversation(mConnectedDeviceName + ": " + supportedPID.toString());
            whichCommand = 0;
            try {
                sendEcuMessage("ATRV");
            }
            catch (BluetoothConnectionNotEstablished ignored) {

            }

        }
    }

    private void checkPids(String tmpmsg) {
        if (tmpmsg.contains("41")) {
            int index = tmpmsg.indexOf("41");

            String pidMsg = tmpmsg.substring(index);

            if (pidMsg.contains("4100")) {

                setPIDsSupported(pidMsg);
            }
        }
    }

    private byte hexStringToByteArray(char s) {
        return (byte) ((Character.digit(s, 16) << 4));
    }

    protected void performCalculations(final String fault) {

        String workingData = "";
        int startIndex = 0;
        troubleCodesArray.clear();

        try{

            if(fault.contains("43"))
            {
                workingData = fault.replaceAll("^43|[\r\n]43|[\r\n]", "");
            }else if(fault.contains("47"))
            {
                workingData = fault.replaceAll("^47|[\r\n]47|[\r\n]", "");
            }

            for (int begin = startIndex; begin < workingData.length(); begin += 4) {
                String dtc = "";
                byte b1 = hexStringToByteArray(workingData.charAt(begin));
                int ch1 = ((b1 & 0xC0) >> 6);
                int ch2 = ((b1 & 0x30) >> 4);
                dtc += dtcLetters[ch1];
                dtc += hexArray[ch2];
                dtc += workingData.substring(begin + 1, begin + 4);

                if (dtc.equals("P0000")) {
                    continue;
                }

                troubleCodesArray.add(dtc);
            }
        }catch(Exception e)
        {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void getFaultInfo(String tmpmsg) {

        String substr = "43";

        int index = tmpmsg.indexOf(substr);

        if (index == -1)
        {
            substr = "47";
            index = tmpmsg.indexOf(substr);
        }

        if (index != -1) {

            tmpmsg = tmpmsg.substring(index);

            if (tmpmsg.substring(0, 2).equals(substr)) {

                performCalculations(tmpmsg);

                String faultCode;
                String faultDesc;

                if (troubleCodesArray.size() > 0) {

                    for (int i = 0; i < troubleCodesArray.size(); i++) {
                        faultCode = troubleCodesArray.get(i);
                        faultDesc = troubleCodes.getFaultCode(faultCode);

                        Log.e(TAG, "Fault Code: " + substr + " : " + faultCode + " desc: " + faultDesc);

                        if (faultCode != null && faultDesc != null) {
                            notifyNewConversation(mConnectedDeviceName + ":  TroubleCode -> " + faultCode + "\n" + faultDesc);
                        } else if (faultCode != null) {
                            notifyNewConversation(mConnectedDeviceName + ":  TroubleCode -> " + faultCode +
                                    "\n" + "Definition not found for code: " + faultCode);
                        }
                    }
                } else {
                    faultCode = "No error found...";
                    notifyNewConversation(mConnectedDeviceName + ":  TroubleCode -> " + faultCode);
                }
            }
        }
    }

    private void calculateEcuValues(int PID, int A, int B) {

        double val;
        int intVal = 0;
        int tempC;

        switch (PID) {

            case 4://PID(04): Engine Load

                // A*100/255
                val = (double) A * 100 / 255;
                EngineLoad engineLoad = new EngineLoad((int) val);

                notifyEngineLoadUpdate(engineLoad);
                notifyNewConversation("Engine Load: " + engineLoad + " %");

                double FuelFlowLH = (massAirflow.getAirflowRate() * engineLoad.getEngineLoad() * mEngineDisplacement / 1000.0 / 714.0) + 0.8;

                if(engineLoad.getEngineLoad() == 0)
                    FuelFlowLH = 0;

                avgConsumption.add(FuelFlowLH);

                FuelConsumption avfFuelConsumption = new FuelConsumption(calculateAverage(avgConsumption));

                if (avfFuelConsumption.getFuelConsumption() < 5) {
                    sendNotification(FUEL_CONSUMPTION_LOW_NOTIFICATION_ID, "Warning! Your fuel " +
                            "consumption is Low.", "Your Mileage is '"
                            + avfFuelConsumption.getFuelConsumption() + " " + avfFuelConsumption.getUnit() + "'.");
                }

                notifyFuelConsumptionUpdate(avfFuelConsumption);
                notifyNewConversation("Fuel Consumption: " + String.format("%10.1f", avfFuelConsumption.getFuelConsumption()).trim() + " l/h");
                break;

            case 5://PID(05): Coolant Temperature

                // A-40
                tempC = A - 40;
                Temperature coolantTemp = new Temperature(tempC, Temperature.Unit.CELSIUS);

                if (coolantTemp.getTemperature() > 110) {
                    sendNotification(COOLANT_AIR_TEMP_HIGH_NOTIFICATION_ID,
                            "Warning! Your Car is Overheated",
                            "Engine Temperature is " + coolantTemp.getTemperature() + " " + coolantTemp.getUnit().value());
                }
                else if (coolantTemp.getTemperature() > 107) {
                    sendNotification(COOLANT_AIR_TEMP_HIGH_NOTIFICATION_ID,
                            "Warning! Warning Car Heated",
                            "Engine Temperature is " + coolantTemp.getTemperature() + " " + coolantTemp.getUnit().value());
                }
                else if (coolantTemp.getTemperature() > 104) {
                    sendNotification(COOLANT_AIR_TEMP_HIGH_NOTIFICATION_ID,
                            "Warning! Your Car Engine Temperature is High",
                            "Engine Temperature is " + coolantTemp.getTemperature() + " " + coolantTemp.getUnit().value());
                }

                notifyCoolantTemperatureUpdate(coolantTemp);
                notifyNewConversation("Engine Temp: " + coolantTemp.getTemperature() + " " + coolantTemp.getUnit().value());

                break;

            case 11://PID(0B)

                // A
                Pressure intakeManPressure = new Pressure(A);
                notifyNewConversation("Intake Man Pressure: " + intakeManPressure.getPressure() + " " + intakeManPressure.getUnit());

                break;

            case 12: //PID(0C): RPM

                //((A*256)+B)/4
                val = ((double) (A * 256) + B) / 4;
                intVal = (int) val;
                RPM rpm = new RPM(intVal);

                notifyRpmUpdate(rpm);

                if (intVal < 500) {
                    sendNotification(RPM_NOTIFICATION_ID, "Warning! You Car Engined RPM is Low", "RPM is currently at " + rpm.getRpm() + ".");
                }

                break;


            case 13://PID(0D): KM

                // A
                Speed speed = new Speed(A);
                SpeedAtTime currentSpeedAtTime = new SpeedAtTime(System.currentTimeMillis(), speed);
                /*lastFewSpeeds.offer(new SpeedAtTime(System.currentTimeMillis(), speed));

                SpeedAtTime speedAtTime = lastFewSpeeds.peek();
                if (speedAtTime != null) {
                    Speed oldSpeed = speedAtTime.getSpeed();

                    Log.d(TAG, "Old Speed: " + oldSpeed.getSpeed() + ", Current Speed : " + speed.getSpeed());

                    if (speed.getSpeed() - oldSpeed.getSpeed() > (oldSpeed.getSpeed() * 4.5)) {
                        sendNotification(AGGRESSIVE_DRIVING_NOTIFICATION_ID, "Warning! Aggressive Driving alert.", "Current Speed '" + speed.getSpeed() + "'.");
                    }
                }*/
                if (lastSpeedAtTime != null) {
                    if ((((double) lastSpeedAtTime.getSpeed().getSpeed() - speed.getSpeed())
                            / Math.abs(lastSpeedAtTime.getTimeStamp() - currentSpeedAtTime.getTimeStamp()))
                            > 4.5) {
                        sendNotification(AGGRESSIVE_DRIVING_NOTIFICATION_ID, "Warning! Aggressive Driving alert.", "Current Speed '" + speed.getSpeed() + "'.");
                    }
                }

                lastSpeedAtTime = currentSpeedAtTime;

                notifySpeedUpdate(speed);

                break;

            case 15://PID(0F): Intake Temperature

                // A - 40
                tempC = A - 40;
                Temperature intakeAirTemp = new Temperature(tempC, Temperature.Unit.CELSIUS);
                if (intakeAirTemp.getTemperature() > 139) {
                    sendNotification(AIR_TEMP_NOTIFICATION_ID, "Warning! You Air Temperature is High.", "Currently at '" + intakeAirTemp.getTemperature() + "'.");
                }
                notifyIntakeTempUpdate(intakeAirTemp);
                notifyNewConversation("Intake Air Temp: " + intakeAirTemp.getTemperature() + " " + intakeAirTemp.getUnit().value());

                break;

            case 16://PID(10): Maf

                // ((256*A)+B) / 100  [g/s]
                double maf = (((double) (256 * A) + B)) / 100;

                if (massAirflow == null) {
                    massAirflow = new AirflowRate(maf);
                }
                else {
                    massAirflow.airflowRate = maf;
                }

                if (massAirflow.airflowRate < 0.5) {
                    sendNotification(MAF_FLOW_NOTIFICATION_ID, "Warning! Your MAF sensor is not working fine.", "Maf Airflow is '" + massAirflow.getAirflowRate() + " " + massAirflow.getUnit() + "'.");
                }
                notifyMAF_AirFlowUpdate(massAirflow);
                notifyNewConversation("Maf Air Flow: " + massAirflow.getAirflowRate() + " " + massAirflow.getUnit());

                break;

            case 17://PID(11)

                //A*100/255
                val = (double) A * 100 / 255;
                ThrottlePosition throttlePosition = new ThrottlePosition((int) val);
//                notifyThrottlePositionUpdate(" Throttle position: " + intVal + " %");
                notifyThrottlePositionUpdate(throttlePosition);
                notifyNewConversation(" Throttle position: " + throttlePosition.getThrottlePosition() + " %");

                break;

            case 35://PID(23)

                // ((A*256)+B)*0.079
                val = ((A * 256) + B) * 0.079;
                Pressure pressure = new Pressure((int) val);
                notifyNewConversation("Fuel Rail Pressure: " + pressure.getPressure() + " " + pressure.getUnit());

                break;

            case 49://PID(31)

                //(256*A)+B km
                val = (A * 256) + B;
                Distance distance = new Distance((int) val, Distance.Unit.KILO_METERS);
                notifyNewConversation("Distance traveled: " + distance.getDistance() + " " + distance.getUnit().value());

                break;

            case 70://PID(46)

                // A-40 [DegC]
                tempC = A - 40;
                Temperature ambientAirTemp = new Temperature(tempC, Temperature.Unit.CELSIUS);
                notifyNewConversation("Ambient Air Temp: " + ambientAirTemp.getTemperature() + " " + ambientAirTemp.getUnit().value());

                break;

            case 92://PID(5C)

                //A-40
                tempC = A - 40;
                Temperature engineOilTemp = new Temperature(tempC, Temperature.Unit.CELSIUS);
                notifyNewConversation("Engine Oil Temp: " + engineOilTemp.getTemperature() + " " + engineOilTemp.getUnit().value());

                break;

            default:
        }
    }

    private double calculateAverage(List<Double> listAvg) {
        Double sum = 0.0;
        for (Double val : listAvg) {
            sum += val;
        }
        return sum / listAvg.size();
    }

    private void analyzePIDS(String dataReceived) {

        if ((dataReceived != null) && (dataReceived.matches("^[0-9A-F]+$"))) {

            dataReceived = dataReceived.trim();

            int index = dataReceived.indexOf("41");

            if (index != -1) {

                String msg = dataReceived.substring(index);

                if (msg.substring(0, 2).equals("41")) {

                    int A, B, PID;

                    PID = Integer.parseInt(msg.substring(2, 4), 16);
                    A = Integer.parseInt(msg.substring(4, 6), 16);
                    try {
                        B = Integer.parseInt(msg.substring(6, 8), 16);
                    }
                    catch (Exception ignored) {
                        B = 0;
                    }

                    calculateEcuValues(PID, A, B);
                }
            }
        }
    }

    private void analyzeMsg(Message msg) {

        String tmpMsg = clearMsg(msg);

        generateVolt(tmpMsg);

        getElmInfo(tmpMsg);

        if (!initialized) {

            sendInitCommands();

        } else {

            checkPids(tmpMsg);

            if (!m_getPIDs && mDetect_PIDs == 1) {
                String sPIDs = "0100";
                try {
                    sendEcuMessage(sPIDs);
                }
                catch (BluetoothConnectionNotEstablished ignored) {

                }
                return;
            }

            if (commandMode) {
                getFaultInfo(tmpMsg);
                return;
            }

            try {
                analyzePIDS(tmpMsg);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                notifyInfoUpdate("Error : " + e.getMessage());
            }

            sendDefaultCommands();
        }
    }

//    @RequiresApi(Q)
//    private void startForegroundQ() {
//        startForeground(NOTIFICATION_ID, getDefaultNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC);
//    }

    @RequiresApi(O)
    private void startForegroundO() {
        startForeground(DEFAULT_NOTIFICATION_ID, getDefaultNotification());
    }

    private void startForegroundCompat()
    {
//        if (Build.VERSION.SDK_INT >= Q) {
//            startForegroundQ();
//        }
        /*else*/ if (Build.VERSION.SDK_INT >= O) {
            startForegroundO();
        }
    }

    @RequiresApi(O)
    private void startMyOwnForeground()
    {
        String channelName = getString(R.string.elm_327_connection_notification_channel_name);
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.GREEN);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        getNotificationManager().createNotificationChannel(chan);

        startForegroundCompat();
    }

    private Intent getAppIntent() {
        return new Intent(getApplicationContext(), MainActivity.class);
    }

    private PendingIntent getDisconnectIntent() {
        Intent stopIntent = new Intent(DISCONNECT_CONNECTION_SERVICE);

        if (Build.VERSION.SDK_INT > O) {
            stopIntent.putExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID);
        }

        return PendingIntent.getBroadcast(this, 0, stopIntent, 0);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void updateDefaultNotificationContent(String content) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        notificationBuilder = notificationBuilder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(content)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), ACTIVITY_REQUEST_CODE, getAppIntent(), FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.disconnect_btn_white, getString(R.string.disconnect_btn), getDisconnectIntent());

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            notificationBuilder = notificationBuilder.setPriority(NotificationManager.IMPORTANCE_HIGH);
        }

        getNotificationManager().notify(DEFAULT_NOTIFICATION_ID, notificationBuilder.build());
    }

    private Notification getDefaultNotification() {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        notificationBuilder = notificationBuilder.setOngoing(true)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content_ready));

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            notificationBuilder = notificationBuilder.setPriority(NotificationManager.IMPORTANCE_HIGH);
        }

        return notificationBuilder.setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), ACTIVITY_REQUEST_CODE, getAppIntent(), FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.disconnect_btn_white, getString(R.string.disconnect_btn), getDisconnectIntent())
                .build();
    }

    private boolean isGroupNotificationCreated = false;

    private void sendNotification(int id, String title, String messageContent) {

        if (!isGroupNotificationCreated) {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

            notificationBuilder = notificationBuilder.setOngoing(true)
                    .setContentTitle(title)
                    .setContentText(messageContent);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                notificationBuilder = notificationBuilder.setPriority(NotificationManager.IMPORTANCE_HIGH);
            }

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);


            String GROUP_KEY_WORK_EMAIL = "com.android.example.WORK_EMAIL";

            Notification notification = notificationBuilder.setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setSound(soundUri)
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(), ACTIVITY_REQUEST_CODE, getAppIntent(), FLAG_UPDATE_CURRENT))
                    .setGroup(GROUP_KEY_WORK_EMAIL)
                    .setGroupSummary(true)
                    .setAutoCancel(false)
                    .build();

            getNotificationManager().notify(SUMMARY_NOTIFICATION_ID, notification);

            isGroupNotificationCreated = true;
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        notificationBuilder = notificationBuilder.setOngoing(true)
                .setContentTitle(title)
                .setContentText(messageContent);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            notificationBuilder = notificationBuilder.setPriority(NotificationManager.IMPORTANCE_HIGH);
        }

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);


        String GROUP_KEY_WORK_EMAIL = "com.android.example.WORK_EMAIL";

        Notification notification = notificationBuilder.setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_launcher)
                .setSound(soundUri)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), ACTIVITY_REQUEST_CODE, getAppIntent(), FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.disconnect_btn_white, getString(R.string.dismiss_btn), getDisconnectIntent())
                .setGroup(GROUP_KEY_WORK_EMAIL)
                .setAutoCancel(false)
                .build();

        getNotificationManager().notify(id, notification);
    }

    private ServiceBroadCastReceiver serviceBroadCastReceiver = new ServiceBroadCastReceiver();

    public class ServiceBroadCastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(serviceBroadCastReceiver);

            if (mBluetoothOdbCallbacks != null) {
                mBluetoothOdbCallbacks.clear();
            }

            if (mWifiOdbCallbacks != null) {
                mWifiOdbCallbacks.clear();
            }

            if (mElm327Callbacks != null) {
                mElm327Callbacks.clear();
            }

            binder.disconnectAllConnectionServices();

            stopForeground();

            stopSelf();
        }
    }

    @Override
    public void onCreate()
    {
        Log.d(TAG, "Created.");

        loadDefaultCommands();

        NOTIFICATION_CHANNEL_ID = getPackageName();

        if (Build.VERSION.SDK_INT > O) {
            startMyOwnForeground();
        }
        else {
            startForeground(DEFAULT_NOTIFICATION_ID, getDefaultNotification());
        }

        IntentFilter intentFilter = new IntentFilter(DISCONNECT_CONNECTION_SERVICE);
        registerReceiver(serviceBroadCastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(serviceBroadCastReceiver);

        if (mBluetoothOdbCallbacks != null) {
            mBluetoothOdbCallbacks.clear();
        }

        if (mWifiOdbCallbacks != null) {
            mWifiOdbCallbacks.clear();
        }

        if (mElm327Callbacks != null) {
            mElm327Callbacks.clear();
        }

        binder.disconnectAllConnectionServices();

        mBtService = null;
        mWifiService = null;

        binder = null;

        if (getNotificationManager() != null) {
            getNotificationManager().cancel(DEFAULT_NOTIFICATION_ID);
        }
    }

    private void stopForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        else {
            stopForeground(true);
        }
    }

    private void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Bundle bundle = intent.getExtras();

        Log.i(TAG, "Action: " + action);
        Log.i(TAG, "Bundle: " + bundle);

        if (action == null || bundle == null) {
            return;
        }

        switch (action) {
            case DISCONNECT_CONNECTION_SERVICE: {

                unregisterReceiver(serviceBroadCastReceiver);

                if (mBluetoothOdbCallbacks != null) {
                    mBluetoothOdbCallbacks.clear();
                }

                if (mWifiOdbCallbacks != null) {
                    mWifiOdbCallbacks.clear();
                }

                if (mElm327Callbacks != null) {
                    mElm327Callbacks.clear();
                }

                binder.disconnectAllConnectionServices();

                stopForeground();
                stopSelf();

                break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "onStartCommand(" + intent + ", " + flags + ", " + startId + ").");

        onHandleIntent(intent);

        return Service.START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private boolean isWifiConnected() {
        if (mWifiService != null)
        {
            return mWifiService.isConnected();
        }

        return false;
    }

    private void connectWifi() {
        if (mWifiService == null)
        {
            mWifiService = new ObdWifiManager(this, mWifiHandler);
        }

        if (mWifiService.getState() == ObdWifiManager.STATE_NONE) {
            mWifiService.connect();
        }
    }

    private void disconnectWifi() {
        if (mWifiService != null)
        {
            mWifiService.disconnect();
        }
    }

    private boolean isBluetoothConnected() {
        return mBtService != null && mBtService.getState() != BluetoothService.STATE_CONNECTED;
    }

    private void prepareBluetooth() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mBtService = new BluetoothService(mBtHandler);
    }

    //  setupChat
    private void connectBluetooth(BluetoothDevice device) {

        if (mBtService == null) {
            // Initialize the BluetoothChatService to perform bluetooth connections
            mBtService = new BluetoothService(mBtHandler);
        }

        tryConnect = true;
        try {
            // Attempt to connect to the device
            mBtService.connect(device);
            currentDevice = device;

        } catch (Exception ignored) {
        }
    }

    private void disconnectBluetooth() {
        if (mBtService != null)
        {
            mBtService.stop();
        }
    }

    private void listenForBluetooth() {
        if (mBtService != null) {
            if (mBtService.getState() == BluetoothService.STATE_NONE) {
                mBtService.start();
            }
        }
    }

    private class MyElm327ConnectionBinder extends Elm327ConnectionBinder {

        @Override
        public boolean isWifiConnected() {

            return Elm327ConnectionService.this.isWifiConnected();
        }

        @Override
        public void connectWifi() {
            Elm327ConnectionService.this.connectWifi();
        }

        private boolean hasWifiCallbackRegistered(WifiODBCallback wifiODBCallback) {
            if (wifiODBCallback == null) {
                return false;
            }

            for (int i = 0; i < mWifiOdbCallbacks.size(); i++) {
                WeakReference<WifiODBCallback> callbackWeakReference = mWifiOdbCallbacks.get(i);

                if (callbackWeakReference.get() == wifiODBCallback) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void registerWifiCallbacks(WifiODBCallback wifiCallback) {
            if (mWifiOdbCallbacks == null) {
                mWifiOdbCallbacks = new ArrayList<>();
            }

            if (!hasWifiCallbackRegistered(wifiCallback)) {
                mWifiOdbCallbacks.add(new WeakReference<>(wifiCallback));
            }
        }

        @Override
        public void unregisterWifiCallbacks(WifiODBCallback wifiODBCallback) {
            if (wifiODBCallback == null) {
                return;
            }

            for (int i = 0; i < mWifiOdbCallbacks.size(); i++) {
                WeakReference<WifiODBCallback> callbackWeakReference = mWifiOdbCallbacks.get(i);

                if (callbackWeakReference.get() == wifiODBCallback) {
                    mWifiOdbCallbacks.remove(callbackWeakReference);

                    return;
                }
            }
        }

        @Override
        public void disconnectWifi() {
            Elm327ConnectionService.this.disconnectWifi();
        }

        @Override
        public boolean isBlueToothConnected() {
            return Elm327ConnectionService.this.isBluetoothConnected();
        }

        @Override
        public void listenForBluetooth() {
            Elm327ConnectionService.this.listenForBluetooth();
        }

        @Override
        public void prepareBluetooth() {
            Elm327ConnectionService.this.prepareBluetooth();
        }

        @Override
        public void connectBlueTooth(BluetoothDevice device) {
            Elm327ConnectionService.this.connectBluetooth(device);
        }

        private boolean hasBluetoothCallbackRegistered(BluetoothODBCallback callback) {
            if (callback == null) {
                return false;
            }

            for (int i = 0; i < mBluetoothOdbCallbacks.size(); i++) {
                WeakReference<BluetoothODBCallback> callbackWeakReference = mBluetoothOdbCallbacks.get(i);

                if (callbackWeakReference.get() == callback) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void registerBluetoothCallbacks(BluetoothODBCallback callback) {
            if (mBluetoothOdbCallbacks == null) {
                mBluetoothOdbCallbacks = new ArrayList<>();
            }

            if (!hasBluetoothCallbackRegistered(callback)) {
                mBluetoothOdbCallbacks.add(new WeakReference<>(callback));
            }
        }

        @Override
        public void unregisterBluetoothCallbacks(BluetoothODBCallback callback) {
            if (callback == null) {
                return;
            }

            for (int i = 0; i < mBluetoothOdbCallbacks.size(); i++) {
                WeakReference<BluetoothODBCallback> callbackWeakReference = mBluetoothOdbCallbacks.get(i);

                if (callbackWeakReference.get() == callback) {
                    mBluetoothOdbCallbacks.remove(callbackWeakReference);

                    return;
                }
            }
        }

        @Override
        public void disconnectBluetooth() {
            Elm327ConnectionService.this.disconnectBluetooth();
        }

        @Override
        public void disconnectAllConnectionServices() {
            try {
                disconnectWifi();
            }
            catch (Exception ignored) {

            }
            try {
                disconnectBluetooth();
            }
            catch (Exception ignored) {

            }
        }

        @Override
        public void sendEcuMessage(String message) throws BluetoothConnectionNotEstablished {
            Elm327ConnectionService.this.sendEcuMessage(message);
        }

        private boolean hasWifiCallbackRegistered(Elm327Callback elm327Callback) {
            if (elm327Callback == null) {
                return false;
            }

            for (int i = 0; i < mElm327Callbacks.size(); i++) {
                WeakReference<Elm327Callback> callbackWeakReference = mElm327Callbacks.get(i);

                if (callbackWeakReference.get() == elm327Callback) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void registerElm327Updates(@NonNull Elm327Callback callback) {
            if (callback == null) {
                return;
            }

            if (mElm327Callbacks == null) {
                mElm327Callbacks = new ArrayList<>();
            }

            if (!hasWifiCallbackRegistered(callback)) {
                mElm327Callbacks.add(new WeakReference<>(callback));
            }
        }

        @Override
        public void unregisterElm327Updates(@NonNull Elm327Callback callback) {
            if (callback == null) {
                return;
            }

            for (int i = 0; i < mElm327Callbacks.size(); i++) {
                WeakReference<Elm327Callback> callbackWeakReference = mElm327Callbacks.get(i);

                if (callbackWeakReference.get() == callback) {
                    mElm327Callbacks.remove(callbackWeakReference);

                    return;
                }
            }
        }
    }
}
