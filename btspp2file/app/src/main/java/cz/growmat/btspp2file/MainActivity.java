package cz.growmat.btspp2file;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
//import android.graphics.ColorSpace;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
//import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.UnsupportedEncodingException;
import java.util.Set;

import cz.growmat.btspp2file.R;

//
//import android.support.v7.app.AppCompatActivity;
//import NotificationService.LocalBinder;


public class MainActivity extends Activity {

    final public static String TAG = MainActivity.class.getName();

    private AlarmManagerBroadcastReceiver mAlarmManagerBroadcastReceiver;
    private DeviceBootReceiver mDeviceBootReceiver;

    boolean mBTSPP2FileServiceBounded;
    BTSPP2FileService mBTSPP2FileService;

    ListView list;
    //CustomListAdapter adapter;
    BaseAdapter adapter;
    //ArrayList<ColorSpace.Model> modelList;

    private ToggleButton tButtonAutoStart;
    private ToggleButton tButtonAutoTest;
    private ToggleButton tButtonWaitForAnswer;
    private TextView mTextViewBluetoothStatus;
    private TextView mTextViewInfoText;
    //private Button mButtonOpenFolder;
    //private TextView mReadBuffer;
    //private Button mTest1Btn;
    //private Button mScanBtn;
    //private Button mSendBtn;
    //private Button mCancelBtn;
    //private Button mOffBtn;
    //private Button mListPairedDevicesBtn;
    //private Button mDiscoverBtn;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;


    private Handler mHandler; // Our main handler that will receive callback notifications
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    SharedPreferences mPrefs;
    SharedPreferences.Editor mEditor;

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        //wl.acquire();
        // Ccreen and CPU will stay awake during this section
        //wl.release();

        Log.d(this.getClass().getSimpleName(), "onCreate");

        setContentView(R.layout.activity_main);

        //modelList = new ArrayList<Model>();
        //adapter = new CustomListAdapter(getApplicationContext(), modelList);
        //list = (ListView) findViewById(R.id.list);
        //list.setAdapter(adapter);

        //LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("MESSAGE"));

        new Thread() {
            public void run() {
                if (!isMyServiceRunning(BTSPP2FileService.class)) {
                    startService(new Intent(getBaseContext(), BTSPP2FileService.class));
                }
                //mServer.Connect(mBTAddress);
            }
        }.start();

        mTextViewBluetoothStatus = (TextView) findViewById(R.id.bluetoothStatus);
        mTextViewInfoText = (TextView) findViewById(R.id.textInfoText);
        /*
        mReadBuffer = (TextView) findViewById(R.id.readBuffer);
        mTest1Btn = (Button) findViewById(R.id.test1);
        mScanBtn = (Button) findViewById(R.id.scan);
        mOffBtn = (Button) findViewById(R.id.off);
        mSendBtn = (Button) findViewById(R.id.send);
        //mDiscoverBtn = (Button) findViewById(R.id.discover);
        */
        //mListPairedDevicesBtn = (Button) findViewById(R.id.buttonPaired);
        tButtonAutoStart = (ToggleButton) findViewById(R.id.toggleButtonAutoStart);
        tButtonAutoTest = (ToggleButton) findViewById(R.id.toggleButtonAutoTest);
        tButtonWaitForAnswer = (ToggleButton) findViewById(R.id.toggleButtonWaitForAnswer);
        //mCancelBtn = (Button) findViewById(R.id.buttonCancel);
        //mButtonOpenFolder = (Button) findViewById(R.id.buttonOpenFolder);

        mBTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView) findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);


        mAlarmManagerBroadcastReceiver = new AlarmManagerBroadcastReceiver();
        mDeviceBootReceiver = new DeviceBootReceiver();

        //SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        //SharedPreferences.Editor mEditor = mPrefs.edit();
        mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        mEditor = mPrefs.edit();


        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    //mReadBuffer.setText(readMessage);
                }

                if (msg.what == CONNECTING_STATUS) {
                    mTextViewBluetoothStatus.setText((String) (msg.obj));
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mTextViewBluetoothStatus.setText("BLUETOOTH NOT FOUND");
            Log.d(TAG, "Bluetooth device not found");
        } else {
            /*
            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });
            */
            /*
            mOffBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOff(v);
                }
            });
            */
            /*
            mSendBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mBTSPP2FileService.btSend("d13,1;");
                }
            });
            */
/*
            mCancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                    notificationManager.cancelAll();
                    mBTSPP2FileService.serviceThreadRun = false;
                    mBTSPP2FileService.btCancel();
                    unbindService(mConnection);
                    stopService(new Intent(getBaseContext(), BTSPP2FileService.class));
                    System.exit(0);

                }
            });
*/
/*
            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listPairedDevices(v);
                }
            });
*/
            /*
            mTest1Btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mBTSPP2FileService.btSend("d13,0;s8;r8;");
                    //Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    //vib.vibrate(500);

                }
            });
            */


/*
            mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    discover(v);
                }
            });*/
        }

        listPairedDevices();
    }



    public void OnClick(View view) {
        switch (view.getId()) {

            case R.id.buttonCancel: {
                NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                notificationManager.cancelAll();
                mBTSPP2FileService.serviceThreadRun = false;
                mBTSPP2FileService.btCancel();
                unbindService(mConnection);
                stopService(new Intent(getBaseContext(), BTSPP2FileService.class));
                System.exit(0);
            }

            /*
            case R.id.buttonPaired: {
                listPairedDevices();//view);
            }
            */

            case R.id.toggleButtonAutoStart: {
                Context context = this.getApplicationContext();
                if (tButtonAutoStart.isChecked()) {
                    //startRepeatingTimer(view);
                    mDeviceBootReceiver.enable(context);
                }
                else {
                    //cancelRepeatingTimer(view);
                    mDeviceBootReceiver.disable(context);
                }
            }

            case R.id.toggleButtonAutoTest: {
                mEditor.putBoolean(Constants.PREFS_NAME_AUTO_TEST, tButtonAutoTest.isChecked()).commit();
                if(tButtonAutoTest.isChecked()) {
                    //if(mBTSPP2FileService != null) {
                    mBTSPP2FileService.startAutoTest();
                    //}
                }
                else {
                    mBTSPP2FileService.stopAutoTest();
                }
            }

            case R.id.toggleButtonWaitForAnswer: {
                mEditor.putBoolean(Constants.PREFS_NAME_WAIT_FOR_ANSWER, tButtonAutoTest.isChecked()).commit();
                mBTSPP2FileService.waitForAnswer = tButtonAutoTest.isChecked();
            }
        }
    }

    public void startRepeatingTimer(View view) {
        Context context = this.getApplicationContext();
        if(mAlarmManagerBroadcastReceiver != null){
            mAlarmManagerBroadcastReceiver.SetAlarm(context);
        }else{
            Log.d(TAG, "Alarm is null");
        }
    }

    public void cancelRepeatingTimer(View view){
        Context context = this.getApplicationContext();
        if(mAlarmManagerBroadcastReceiver != null){
            mAlarmManagerBroadcastReceiver.CancelAlarm(context);
        }
        else {
            Log.d(TAG, "Alarm is null");
        }
    }

    public void onetimeTimer(View view){
        Context context = this.getApplicationContext();
        if(mAlarmManagerBroadcastReceiver != null){
            mAlarmManagerBroadcastReceiver.setOnetimeTimer(context);
        }
        else {
            Log.d(TAG, "Alarm is null");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

        Context context = this.getApplicationContext();

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(9011);

        boolean alarmUp = (PendingIntent.getBroadcast(context, 0,
                new Intent(context, AlarmManagerBroadcastReceiver.class),
                PendingIntent.FLAG_NO_CREATE) != null);

        if(mDeviceBootReceiver.getComponentEnabledSetting(context) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            tButtonAutoStart.setChecked(true);
        else
            tButtonAutoStart.setChecked(false);

        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        if(mPrefs.getBoolean(Constants.PREFS_NAME_AUTO_TEST, true))
            tButtonAutoTest.setChecked(true);
        else
            tButtonAutoTest.setChecked(false);

        if(mPrefs.getBoolean(Constants.PREFS_NAME_WAIT_FOR_ANSWER, true))
            tButtonWaitForAnswer.setChecked(true);
        else
            tButtonWaitForAnswer.setChecked(false);

        mTextViewInfoText.setText(" PATH: " + Environment.getExternalStorageDirectory().getAbsolutePath() + mPrefs.getString(Constants.PREFS_NAME_PATH, Constants.DEFAULT_PATH) +
                "\n RX FILE: " + Constants.DEFAULT_RX_FILENAME +
                "\n TX FILE: " + Constants.DEFAULT_TX_FILENAME);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_main, menu);//Menu Resource, Menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /*
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        */
        return super.onOptionsItemSelected(item);
    }

    // TODO:
    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onNotice");

  /*
        // String pack = intent.getStringExtra("package");
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        //int id = intent.getIntExtra("icon",0);

        String message = intent.getStringExtra("MESSAGE");
        if(message != null) {

            if (message.equals("right")) {
            }
        }

        Context remotePackageContext = null;
        try {
            byte[] byteArray = intent.getByteArrayExtra("icon");
            Bitmap bmp = null;
            if (byteArray != null) {
                bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            }
            Model model = new Model();
            model.setName(title + " " + text);
            model.setImage(bmp);

            if (modelList != null) {
                modelList.add(model);
                adapter.notifyDataSetChanged();
            } else {
                modelList = new ArrayList<Model>();
                modelList.add(model);
                adapter = new CustomListAdapter(getApplicationContext(), modelList);
                list = (ListView) findViewById(R.id.list);
                list.setAdapter(adapter);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        */
        }
    };

    private void bluetoothOn(View view) {
        if (!mBTAdapter.isEnabled()) {
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            if(mBTAdapter.enable());
                mTextViewBluetoothStatus.setText("BLUETOOTH ENABLED");
            Log.d(TAG, "Bluetooth enabled");
        } else {
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mTextViewBluetoothStatus.setText("BLUETOOTH ENABLED");
            } else
                mTextViewBluetoothStatus.setText("BLUETOOTH DISABLED");
        }
    }

    private void bluetoothOff(View view) {
        mBTAdapter.disable(); // Turn off
        mTextViewBluetoothStatus.setText("BLUETOOTH DISABLED");
        Log.d(TAG, "Bluetooth disabled");
    }

    private void discover() {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Log.d(TAG, "Discovery stopped");
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // Clear items
                mBTAdapter.startDiscovery();
                Log.d(TAG, "Discovery started");
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            } else {
                Log.d(TAG, "Bluetooth not on");
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name to the list
                mBTArrayAdapter.add(device.getName() + " " + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices() {

        //discover();

        mPairedDevices = mBTAdapter.getBondedDevices();
        if (mBTAdapter.isEnabled()) {
            mBTArrayAdapter.clear(); // Clear items
            // Put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices) {
                mBTArrayAdapter.add(device.getName() + " " + device.getAddress());

            }
        } else {
        }
        mBTArrayAdapter.notifyDataSetChanged();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            String mInfo = ((TextView) v).getText().toString();
            final String mBTAddress = mInfo.substring(mInfo.length() - 17);
            final String mBTName = mInfo.substring(0, mInfo.length() - 17);
            /*
            // Spawn a new thread to avoid blocking the GUI one
            new Thread() {
                public void run() {
                    if(!isMyServiceRunning(BTSPP2FileService.class)) {
                        startService(new Intent(getBaseContext(), BTSPP2FileService.class));
                    }
                    mBTSPP2FileService.Connect(mBTAddress);
                }
            }.start();
            */
            if(!isMyServiceRunning(BTSPP2FileService.class)) {
                startService(new Intent(getBaseContext(), BTSPP2FileService.class));
            }
            mBTSPP2FileService.Connect(mBTAddress);
        }
    };
        /*
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            //if(mServer.mConnectedThread != null) {
            //    Toast.makeText(getBaseContext(), "Already connected", Toast.LENGTH_SHORT).show();
            //    return;
            //}

            mBluetoothStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0, info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread() {
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (fail == false) {
                        //mConnectedThread = new ConnectedThread(mBTSocket);
                        //mConnectedThread.start();

                        //startService(new Intent(MainActivity.this, MyService.class));
                        //if(mServer.mConnectedThread == null) {
                            //if (!mServer.mConnectedThread.isAlive()) {

                                mServer.btStart(mBTSocket);

                                mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                        .sendToTarget();
                            //}
                        //}
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }
    */
    /*
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        mmInStream.read(buffer);
                    }
                    // Send the obtained bytes to the UI activity

                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        // Call this from the main activity to shutdown the connection
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
    */

    /*
    NotificationManager notificationManager;
    private void showNotification(String title, String message, int defaults)
    {
        //NotificationManager notificationManager;
        //notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.ic_launcher, "A Notification", System.currentTimeMillis());

        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.defaults |= defaults;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setLatestEventInfo(MainActivity.this, title, message, pendingIntent);
        notificationManager.notify(1, notification);
    }
    */

    @Override
    protected void onStart() {
        super.onStart();

        Intent mIntent = new Intent(this, BTSPP2FileService.class);
        //mIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);
    };

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service is disconnected");
            mBTSPP2FileServiceBounded = false;
            mBTSPP2FileService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service is connected");

            mBTSPP2FileServiceBounded = true;
            BTSPP2FileService.LocalBinder mLocalBinder = (BTSPP2FileService.LocalBinder) service;
            mBTSPP2FileService = mLocalBinder.getServerInstance();
            mBTSPP2FileService.mHandler = mHandler;

            mTextViewBluetoothStatus.setText(mBTSPP2FileService.BTStatus);
            //showNotification(mBluetoothSerialBridgeService.BTStatus, null, 0);

            /*
            mBluetoothStatus.setText("UNKNOWN");
            if(mServer.connectedThread != null)
                if(mServer.connectedThread.socket.isConnected())
                    mBluetoothStatus.setText("CONNECTED " + mServer.BTAddress);
            */
        }

        //@Override
        public void onStop() {
            if (mBTSPP2FileServiceBounded) {
                unbindService(mConnection);
                mBTSPP2FileServiceBounded = false;
            }
        }
    };
}















