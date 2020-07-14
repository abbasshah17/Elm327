package com.obdelm327pro;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.obdelm327pro.service.AirflowRate;
import com.obdelm327pro.service.BluetoothConnectionNotEstablished;
import com.obdelm327pro.service.BluetoothODBCallback;
import com.obdelm327pro.service.BluetoothState;
import com.obdelm327pro.service.DeviceInfo;
import com.obdelm327pro.service.Elm327Callback;
import com.obdelm327pro.service.Elm327ConnectionBinder;
import com.obdelm327pro.service.Elm327ConnectionService;
import com.obdelm327pro.service.EngineLoad;
import com.obdelm327pro.service.FuelConsumption;
import com.obdelm327pro.service.RPM;
import com.obdelm327pro.service.Speed;
import com.obdelm327pro.service.Temperature;
import com.obdelm327pro.service.ThrottlePosition;
import com.obdelm327pro.service.Voltage;
import com.obdelm327pro.service.WifiODBCallback;
import com.obdelm327pro.service.WifiState;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";


    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static boolean actionbar = true;
    MenuItem itemTemp;
    GaugeSpeed speed;
    GaugeRpm rpm;
//    BluetoothDevice currentdevice;
    boolean commandMode = false, initialized = false, m_get_Pids = false, tryConnect = false, defaultStart = false;

    Intent serverIntent = null;
    String VOLTAGE = "ATRV",
            RESET = "ATZ";
    Toolbar toolbar;
    AppBarLayout appbar;
    private PowerManager.WakeLock wl;
    private Menu menu;
    private EditText mOutEditText;
    private Button mSendButton, mPIDS_Button, mTroubleCodes, mClearTroubleCodes, mClearList;
    private ListView mConversationView;
    private TextView engineLoad, Fuel, voltage, coolantTemperature, Status, loadText, voltText, tempText, centerText, info, airTempText, airTemperature, MAF_text, MAF;
    private String mConnectedDeviceName = "Ecu";
    private int FaceColor = 0;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    // The Handler that gets information back from the BluetoothChatService
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // If the action is a key-up event on the return key, send the message
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        String message = view.getText().toString();
                        sendEcuMessage(message);
                    }
                    return true;
                }
            };

    private WifiStateChangeCallback mWifiStateChangeCallback = new WifiStateChangeCallback();

    private class WifiStateChangeCallback implements WifiODBCallback {

        @Override
        public void onStateChanged(@NonNull WifiState state) {
            switch (state) {
                case STATE_NONE: {
                    Status.setText(R.string.title_not_connected);
                    itemTemp = menu.findItem(R.id.menu_connect_wifi);
                    itemTemp.setTitle(R.string.connectwifi);

                    resetvalues();

                    break;
                }

                case STATE_CONNECTED: {

                    Status.setText(getString(R.string.title_connected_to, "ELM327 WIFI"));
                    try {
                        itemTemp = menu.findItem(R.id.menu_connect_wifi);
                        itemTemp.setTitle(R.string.disconnectwifi);
                    } catch (Exception ignored) {
                    }

                    resetvalues();
                    sendEcuMessage(RESET);

                    break;
                }

                case STATE_CONNECTING: {

                    Status.setText(R.string.title_connecting);
                    info.setText(R.string.tryconnectwifi);

                    break;
                }
            }
        }
    }

    private BluetoothStateChangeCallback mBluetoothStateChangeCallback = new BluetoothStateChangeCallback();

    private class BluetoothStateChangeCallback implements BluetoothODBCallback {

        @Override
        public void onStateChanged(@NonNull BluetoothState state) {
            switch (state) {
                case STATE_CONNECTING: {

                    Status.setText(R.string.title_connecting);
                    info.setText(R.string.tryconnectbt);

                    break;
                }

                case STATE_CONNECTED: {

                    Status.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
                    info.setText(R.string.title_connected);

                    try {
                        itemTemp = menu.findItem(R.id.menu_connect_bt);
                        itemTemp.setTitle(R.string.disconnectbt);
                        info.setText(R.string.title_connected);
                    }
                    catch (Exception ignored) {
                    }

                    resetvalues();
                    break;
                }

                case STATE_LISTEN:
                case STATE_NONE: {

                    Status.setText(R.string.title_not_connected);
                    itemTemp = menu.findItem(R.id.menu_connect_bt);
                    itemTemp.setTitle(R.string.connectbt);

                    resetvalues();

                    break;
                }
            }
        }
    }

    private Elm327StatusUpdatesCallback mElm327StatusUpdatesCallback = new Elm327StatusUpdatesCallback();

    private class Elm327StatusUpdatesCallback implements Elm327Callback {

        @Override
        public void onDeviceInfo(@NonNull DeviceInfo deviceInfo) {
            if (Status != null) {
                String status = deviceInfo.getDeviceName() + " " + deviceInfo.getDeviceProtocol();
                Status.setText(status);
            }
        }

        @Override
        public void onInfo(@NonNull String info) {
            if (MainActivity.this.info != null) {
                MainActivity.this.info.setText(info);
            }
        }

        @Override
        public void onNewConversation(@NonNull String conversation) {
            if (mConversationArrayAdapter != null) {
                mConversationArrayAdapter.add(conversation);
            }
        }

        @Override
        public void toastMessage(@NonNull String message) {
            if (getApplicationContext() != null) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onVoltageUpdate(@NonNull Voltage voltage) {
            String voltageText = voltage.getVoltage() + voltage.getUnit();
            if (MainActivity.this.voltage != null) {
                MainActivity.this.voltage.setText(voltageText);
            }
        }

        @Override
        public void onEngineLoadUpdate(@NonNull EngineLoad engineLoad) {
            if (MainActivity.this.engineLoad != null) {
                String engineLoadText = engineLoad.getEngineLoad() + "%";
                MainActivity.this.engineLoad.setText(engineLoadText);
            }
        }

        @Override
        public void onFuelConsumptionUpdate(@NonNull FuelConsumption fuelConsumption) {
            if (Fuel != null) {
                String fuelConsumptionText = String.format("%10.1f", fuelConsumption.getFuelConsumption()).trim() + " " + fuelConsumption.getUnit();
                Fuel.setText(fuelConsumptionText);
            }
        }

        @Override
        public void onCoolantTemperatureUpdate(@NonNull Temperature coolantTemperature) {
            if (MainActivity.this.coolantTemperature != null) {
                String coolantTempText = coolantTemperature.getTemperature() + " " + coolantTemperature.getUnit().value();
                MainActivity.this.coolantTemperature.setText(coolantTempText);
            }
        }

        @Override
        public void onRpmUpdate(@NonNull RPM rpm) {
            if (MainActivity.this.rpm != null) {
                int transformedRpm = rpm.getRpm() / 100;
                MainActivity.this.rpm.setTargetValue(transformedRpm);
            }
        }

        @Override
        public void onSpeedUpdate(@NonNull Speed speed) {
            if (MainActivity.this.speed != null) {
                MainActivity.this.speed.setTargetValue(speed.getSpeed());
            }
        }

        @Override
        public void onIntakeTemperatureUpdate(@NonNull Temperature intakeTemperature) {
            if (airTemperature != null) {
                String intakeTempText = intakeTemperature.getTemperature() + " " + intakeTemperature.getUnit().value();
                airTemperature.setText(intakeTempText);
            }
        }

        @Override
        public void onMAF_AirFlowUpdate(@NonNull AirflowRate maf) {
            if (MAF != null) {
                String massAirflowRateText = maf.getAirflowRate() + " " + maf.getUnit();
                MAF.setText(massAirflowRateText);
            }
        }

        @Override
        public void onThrottlePositionUpdate(@NonNull ThrottlePosition throttle) {

        }
    }
    private Elm327ServiceConnection serviceConnection;

    private class Elm327ServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Component : " + name + "onServiceConnected() " + service.toString());

            connectionService = (Elm327ConnectionBinder) service;

            connectionService.registerWifiCallbacks(mWifiStateChangeCallback);
            connectionService.registerBluetoothCallbacks(mBluetoothStateChangeCallback);
            connectionService.registerElm327Updates(mElm327StatusUpdatesCallback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Component : " + name + "onServiceConnected() ");

            connectionService.unregisterWifiCallbacks(mWifiStateChangeCallback);
            connectionService.unregisterBluetoothCallbacks(mBluetoothStateChangeCallback);
            connectionService.unregisterElm327Updates(mElm327StatusUpdatesCallback);

            connectionService = null;
        }
    }

    private Elm327ConnectionBinder connectionService;

    private void startConnectionService() {
        Intent intent = new Intent(getApplicationContext(), Elm327ConnectionService.class);

        startService(intent);
    }

    protected void bindToService() {

        serviceConnection = new Elm327ServiceConnection();

        Intent intent = new Intent(this, Elm327ConnectionService.class);

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
    }

    protected void unbindFromService() {
        if (serviceConnection == null) {
            return;
        }

        unbindService(serviceConnection);

        serviceConnection = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gauges);

        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        appbar = findViewById(R.id.appbar);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, getPackageName() + ":connectionService");
        wl.acquire();

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Status = findViewById(R.id.Status);
        engineLoad = findViewById(R.id.Load);
        Fuel = findViewById(R.id.Fuel);
        coolantTemperature = findViewById(R.id.Temp);
        voltage = findViewById(R.id.Volt);
        loadText = findViewById(R.id.Load_text);
        tempText = findViewById(R.id.Temp_text);
        voltText = findViewById(R.id.Volt_text);
        centerText = findViewById(R.id.Center_text);
        info = findViewById(R.id.info);
        airTempText = findViewById(R.id.Airtemp_text);
        airTemperature = findViewById(R.id.Airtemp);
        MAF_text = findViewById(R.id.Maf_text);
        MAF = findViewById(R.id.Maf);
        speed = findViewById(R.id.GaugeSpeed);
        rpm = findViewById(R.id.GaugeRpm);

        mOutEditText = findViewById(R.id.edit_text_out);
        mPIDS_Button = findViewById(R.id.button_pids);
        mSendButton = findViewById(R.id.button_send);
        mClearTroubleCodes = findViewById(R.id.button_clearcodes);
        mClearList = findViewById(R.id.button_clearlist);
        mTroubleCodes = findViewById(R.id.button_troublecodes);
        mConversationView = findViewById(R.id.in);


        invisiblecmd();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
        else
        {
            if (connectionService != null) {
                connectionService.listenForBluetooth();
            }
        }

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                // Get the Item from ListView
                View view = super.getView(position, convertView, parent);

                // Initialize a TextView for ListView each Item
                TextView tv = view.findViewById(R.id.listText);

                // Set the text color of TextView (ListView Item)
                tv.setTextColor(Color.parseColor("#3ADF00"));
                tv.setTextSize(10);

                // Generate ListView Item using TextView
                return view;
            }
        };

        mConversationView.setAdapter(mConversationArrayAdapter);

        mPIDS_Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String sPIDs = "0100";
                m_get_Pids = false;
                if (connectionService != null) {
                    sendEcuMessage(sPIDs);
                }
            }
        });
        // Initialize the send button with a listener that for click events

        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                String message = mOutEditText.getText().toString();
                sendEcuMessage(message);
            }
        });

        mClearTroubleCodes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String clearCodes = "04";
                sendEcuMessage(clearCodes);
            }
        });

        mClearList.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mConversationArrayAdapter.clear();
            }
        });

        mTroubleCodes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                String troubleCodes = "03";
                sendEcuMessage(troubleCodes);
            }
        });

        mOutEditText.setOnEditorActionListener(mWriteListener);

        RelativeLayout rlayout = findViewById(R.id.mainscreen);
        rlayout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    ActionBar actionBar = getSupportActionBar();
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) Status.getLayoutParams();
                    if (actionbar) {
                        //toolbar.setVisibility(View.GONE);
                        if (actionBar != null) {
                            actionBar.hide();
                        }
                        actionbar = false;

                        lp.setMargins(0, 5, 0, 0);
                    } else {
                        //toolbar.setVisibility(View.VISIBLE);
                        if (actionBar != null) {
                            actionBar.show();
                        }
                        actionbar = true;
                        lp.setMargins(0, 0, 0, 0);
                    }

                    setgaugesize();
                    Status.setLayoutParams(lp);

                } catch (Exception ignored) {
                }
            }
        });

        getPreferences();

        resetgauges();

        startConnectionService();

        bindToService();
    }

    private void sendEcuMessage(String msg) {
        try {
            if (connectionService != null) {
                connectionService.sendEcuMessage(msg);
            }
        }
        catch (BluetoothConnectionNotEstablished ex) {
            Toast.makeText(MainActivity.this, R.string.not_connected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        this.menu = menu;

        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.menu_connect_bt:

                if (connectionService != null)
                {
                    if (connectionService.isWifiConnected())
                    {
                        Toast.makeText(getApplicationContext(), "First Disconnect WIFI Device.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    return false;
                }

                if (connectionService != null)
                    connectionService.prepareBluetooth();

                if (item.getTitle().equals("ConnectBT")) {
                    // Launch the DeviceListActivity to see devices and do scan
                    serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    if (connectionService != null)
                    {
                        connectionService.disconnectBluetooth();
                        item.setTitle(R.string.connectbt);
                    }
                }

                return true;
            case R.id.menu_connect_wifi:

                if (item.getTitle().equals("ConnectWIFI")) {

                    if (connectionService != null) {
                        connectionService.connectWifi();
                    }
                } else {
                    if (connectionService != null)
                    {
                        connectionService.disconnectWifi();
                        item.setTitle(R.string.connectwifi);
                    }
                }

                return true;
            case R.id.menu_terminal:

                if (item.getTitle().equals("Terminal")) {
                    commandMode = true;
                    visiblecmd();
                    item.setTitle(R.string.gauges);
                } else {
                    invisiblecmd();
                    item.setTitle(R.string.terminal);
                    commandMode = false;
                    sendEcuMessage(VOLTAGE);
                }
                return true;

            case R.id.menu_settings:

                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, Prefs.class);
                startActivity(serverIntent);

                return true;
            case R.id.menu_exit:
                exit();

                return true;
            case R.id.menu_reset:
                resetvalues();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connectDevice(Intent data) {
        tryConnect = true;
        // Get the device MAC address
        if (data.getExtras() == null)  {
            return;
        }

        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        try {
            // Attempt to connect to the device
            if (connectionService != null) {
                connectionService.connectBlueTooth(device);
            }

        } catch (Exception ignored) {
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == MainActivity.RESULT_OK) {
                    connectDevice(data);    //  Service connectBT2
                }
                break;

            case REQUEST_ENABLE_BT:

                if (connectionService != null)
                    connectionService.prepareBluetooth();

                if (resultCode == MainActivity.RESULT_OK) {
                    serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    Toast.makeText(getApplicationContext(), "BT device not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setDefaultOrientation();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        getPreferences();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindFromService();

//        if (connectionService != null) connectionService.disconnectAllConnectionServices();

//        wl.release();
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferences();
        setDefaultOrientation();
        resetvalues();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_BACK) {

            if (!commandMode) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage("Are you sure you want exit?");
                alertDialogBuilder.setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                exit();
                            }
                        });

                alertDialogBuilder.setNegativeButton("cancel",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {

                            }
                        });

                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            } else {
                commandMode = false;
                invisiblecmd();
                MenuItem item = menu.findItem(R.id.menu_terminal);
                item.setTitle(R.string.terminal);
                sendEcuMessage(VOLTAGE);
            }

            return false;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void exit() {
        if (connectionService != null) connectionService.disconnectBluetooth();
        wl.release();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void getPreferences() {
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());

        String faceColor = preferences.getString("FaceColor", "0");

        if (faceColor != null) {
            FaceColor = Integer.parseInt(faceColor);
        }

        rpm.setFace(FaceColor);
        speed.setFace(FaceColor);
    }

    private void setDefaultOrientation() {

        try {

            settextsixe();
            setgaugesize();

        } catch (Exception ignored) {
        }
    }

    private void settextsixe() {
        int txtsize = 14;
        int sttxtsize = 12;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindow().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        Status.setTextSize(sttxtsize);
        Fuel.setTextSize(txtsize + 2);
        coolantTemperature.setTextSize(txtsize);
        engineLoad.setTextSize(txtsize);
        voltage.setTextSize(txtsize);
        tempText.setTextSize(txtsize);
        loadText.setTextSize(txtsize);
        voltText.setTextSize(txtsize);
        airTempText.setTextSize(txtsize);
        airTemperature.setTextSize(txtsize);
        MAF_text.setTextSize(txtsize);
        MAF.setTextSize(txtsize);
        info.setTextSize(sttxtsize);
    }

    public void invisiblecmd() {
        mConversationView.setVisibility(View.INVISIBLE);
        mOutEditText.setVisibility(View.INVISIBLE);
        mSendButton.setVisibility(View.INVISIBLE);
        mPIDS_Button.setVisibility(View.INVISIBLE);
        mTroubleCodes.setVisibility(View.INVISIBLE);
        mClearTroubleCodes.setVisibility(View.INVISIBLE);
        mClearList.setVisibility(View.INVISIBLE);
        rpm.setVisibility(View.VISIBLE);
        speed.setVisibility(View.VISIBLE);
        engineLoad.setVisibility(View.VISIBLE);
        Fuel.setVisibility(View.VISIBLE);
        voltage.setVisibility(View.VISIBLE);
        coolantTemperature.setVisibility(View.VISIBLE);
        loadText.setVisibility(View.VISIBLE);
        voltText.setVisibility(View.VISIBLE);
        tempText.setVisibility(View.VISIBLE);
        centerText.setVisibility(View.VISIBLE);
        info.setVisibility(View.VISIBLE);
        airTempText.setVisibility(View.VISIBLE);
        airTemperature.setVisibility(View.VISIBLE);
        MAF_text.setVisibility(View.VISIBLE);
        MAF.setVisibility(View.VISIBLE);
    }

    public void visiblecmd() {
        rpm.setVisibility(View.INVISIBLE);
        speed.setVisibility(View.INVISIBLE);
        engineLoad.setVisibility(View.INVISIBLE);
        Fuel.setVisibility(View.INVISIBLE);
        voltage.setVisibility(View.INVISIBLE);
        coolantTemperature.setVisibility(View.INVISIBLE);
        loadText.setVisibility(View.INVISIBLE);
        voltText.setVisibility(View.INVISIBLE);
        tempText.setVisibility(View.INVISIBLE);
        centerText.setVisibility(View.INVISIBLE);
        info.setVisibility(View.INVISIBLE);
        airTempText.setVisibility(View.INVISIBLE);
        airTemperature.setVisibility(View.INVISIBLE);
        MAF_text.setVisibility(View.INVISIBLE);
        MAF.setVisibility(View.INVISIBLE);
        mConversationView.setVisibility(View.VISIBLE);
        mOutEditText.setVisibility(View.VISIBLE);
        mSendButton.setVisibility(View.VISIBLE);
        mPIDS_Button.setVisibility(View.VISIBLE);
        mTroubleCodes.setVisibility(View.VISIBLE);
        mClearTroubleCodes.setVisibility(View.VISIBLE);
        mClearList.setVisibility(View.VISIBLE);
    }

    private void setgaugesize() {
        Display display = getWindow().getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        if (width > height) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(height, height);

            lp.addRule(RelativeLayout.BELOW, findViewById(R.id.Load).getId());
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.setMargins(0, 0, 50, 0);
            rpm.setLayoutParams(lp);
            rpm.getLayoutParams().height = height;
            rpm.getLayoutParams().width = (width - 100) / 2;

            lp = new RelativeLayout.LayoutParams(height, height);
            lp.addRule(RelativeLayout.BELOW, findViewById(R.id.Load).getId());
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lp.setMargins(50, 0, 0, 0);
            speed.setLayoutParams(lp);
            speed.getLayoutParams().height = height;
            speed.getLayoutParams().width = (width - 100) / 2;

        } else if (width < height) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, width);

            lp.addRule(RelativeLayout.BELOW, findViewById(R.id.Fuel).getId());
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            lp.setMargins(25, 5, 25, 5);
            rpm.setLayoutParams(lp);
            rpm.getLayoutParams().height = height/2;
            rpm.getLayoutParams().width = width;

            lp = new RelativeLayout.LayoutParams(width, width);
            lp.addRule(RelativeLayout.BELOW, findViewById(R.id.GaugeRpm).getId());
            //lp.addRule(RelativeLayout.ABOVE,findViewById(R.id.info).getId());
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            lp.setMargins(25, 5, 25, 5);
            speed.setLayoutParams(lp);
            speed.getLayoutParams().height = height/2;
            speed.getLayoutParams().width = (width);
        }
    }

    public void resetgauges() {

        speed.setTargetValue(220);
        rpm.setTargetValue(80);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        speed.setTargetValue(0);
                        rpm.setTargetValue(0);
                    }
                });
            }
        }).start();
    }

    public void resetvalues() {

        engineLoad.setText("0 %");
        voltage.setText("0 V");
        coolantTemperature.setText("0 C°");
        info.setText("");
        airTemperature.setText("0 C°");
        MAF.setText("0 g/s");
        Fuel.setText("0 - 0 l/h");

        m_get_Pids = false;
        initialized = false;
        defaultStart = false;
        mConversationArrayAdapter.clear();

        resetgauges();

        sendEcuMessage(RESET);
    }

}