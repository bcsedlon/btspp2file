package cz.growmat.btspp2file;

/**
 * Created by pravoj01 on 2.4.2017.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
//import android.support.annotation.Nullable;
//import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Time;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.growmat.btspp2file.R;

public class BTSPP2FileService extends Service {

    final public static String TAG = BTSPP2FileService.class.getName();

    public StreamServiceThread[] streamServiceThread = {null, null, null, null, null, null, null};
    public boolean[] serviceThreadRun = {true, true, true, true, true, true, true};
    boolean waitForAnswer = false;

    private static BluetoothSocket[] mBTSocket = {null, null, null, null, null, null, null}; // bi-directional client-to-client data path
    private static BluetoothAdapter mBTAdapter = null;
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    public static String[] BTAddress = {null, null, null, null, null, null, null};
    //public String BTStatus;
    String[] BTStatus = {null, null, null, null, null, null, null};

    // Defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    public Handler mHandler; // Our main handler that will receive callback notifications

    PowerManager mPowerManager;
    PowerManager.WakeLock mWakeLock;

    private static boolean mAutoTest = false;
    private boolean autoTestRunning = false;

    private AtomicBoolean[] btStarting = {null, null, null, null, null, null, null};



    public void startAutoTest() {
        if(autoTestRunning)
            return;
        autoTestRunning = true;

        new Thread() {
            public void run() {
                while(autoTestRunning) {
                    SystemClock.sleep(250);
                    //if(mServer != null) {
                    for(int i = 0 ; i < 7; i++) {
                        if (mAutoTest) {
                            btSend(i, "d10,1;");
                            btSend(i, "d9,0;");
                        } else {
                            btSend(i, "d10,0;");
                            btSend(i, "d9,1;");
                        }
                    }
                    mAutoTest = !mAutoTest;
                    //}
                }
            }
        }.start();
    }

    public void stopAutoTest() {
        autoTestRunning = false;
    }

    public void Connect(int index, String BTAddress) {
        //showNotification("CONNECTING " + String.valueOf(index) + ": " + BTAddress, null, 0);

        this.BTAddress[index] = BTAddress;
        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        SharedPreferences.Editor mEditor = mPrefs.edit();
        if(index == 0)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_0, BTAddress).commit();
        if(index == 1)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_1, BTAddress).commit();
        if(index == 2)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_2, BTAddress).commit();
        if(index == 3)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_3, BTAddress).commit();
        if(index == 4)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_4, BTAddress).commit();
        if(index == 5)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_5, BTAddress).commit();
        if(index == 6)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_6, BTAddress).commit();

        serviceThreadRun[index] = true;
        Connect(index);
    }

    public void Disconnect(final int index) {
        BTStatus[index] = null; //"DISCONNECTED " + String.valueOf(index) + ": " + BTAddress[index];
        showNotification("DISCONNECTED " + String.valueOf(index) + ": " + BTAddress[index], null, 0);
        if (mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, getBTStatusString()).sendToTarget();

        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        SharedPreferences.Editor mEditor = mPrefs.edit();
        if(index == 0)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_0, null).commit();
        if(index == 1)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_1, null).commit();
        if(index == 2)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_2, null).commit();
        if(index == 3)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_3, null).commit();
        if(index == 4)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_4, null).commit();
        if(index == 5)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_5, null).commit();
        if(index == 6)
            mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS_6, null).commit();

        BTAddress[index] = null;

        serviceThreadRun[index] = false;
        btCancel(index);
    }

    public void Connect(final int index) {
        BTStatus[index] = "CONNECTING " + String.valueOf(index) + ": " + BTAddress[index];
        showNotification(BTStatus[index], null, 0);
        if (mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, getBTStatusString()).sendToTarget();

        if(BTAddress[index] != null) {
            btCancel(index);
            //if (btStarting.compareAndSet(false, true)) {
            new Thread() {
                public void run() {
                    while (serviceThreadRun[index]) {
                        if (btStart(index))
                            break;
                    }
                }
            }.start();
            //}
        }
        return;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    PowerManager.WakeLock wl;

    @Override
    public void onCreate() {
        //BTStatus = "STARTING UP";
        showNotification("STARTING UP", null, 0);
        //if(mHandler != null)
        //    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();

        //PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
        mWakeLock.acquire();
        //LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        for(int i = 0; i < 7; i++) {
            btStarting[i] = new AtomicBoolean(false);
        }

        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        //wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        //wl.acquire();

        Log.d(TAG, "Service Starting...");

        Time now = new Time();
        now.setToNow();
        String s = now.toString() + '\n';
        Utils.saveToFile(-1, Constants.DEFAULT_PATH, Constants.DEFAULT_LOG_FILENAME, s.getBytes(), s.getBytes().length);

        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        BTAddress[0] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_0, null);
        BTAddress[1] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_1, null);
        BTAddress[2] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_2, null);
        BTAddress[3] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_3, null);
        BTAddress[4] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_4, null);
        BTAddress[5] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_5, null);
        BTAddress[6] = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS_6, null);

        waitForAnswer = mPrefs.getBoolean(Constants.PREFS_NAME_WAIT_FOR_ANSWER, false);


        //mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        for(int i = 0; i < 7; i++) {
            if(BTAddress[i] != null) {
                final int i2 = i;
                new Thread() {
                    public void run() {
                        while (serviceThreadRun[i2]) {
                            if (btStart(i2))
                                break;
                        }
                    }
                }.start();
            }
        }

       //autoTest = mPrefs.getBoolean(Constants.PREFS_NAME_AUTO_TEST, true);
        if(mPrefs.getBoolean(Constants.PREFS_NAME_AUTO_TEST, true)) {
            startAutoTest();
        }

        Log.d(TAG, "Service Started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWakeLock.release();
        for(int i = 0; i < 7; i++) {
            btCancel(i);
        }
        Log.d(TAG, "Service Destroyed");
    }

    IBinder mBinder = new LocalBinder();

    //@Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public BTSPP2FileService getServerInstance() {
            return BTSPP2FileService.this;
        }
    }

    // Bluetooth

    public boolean btCancel(int index){
        if (streamServiceThread[index] != null) {
            streamServiceThread[index].cancel();
            streamServiceThread[index] = null;
            try {
                mBTSocket[index].close();
            } catch (IOException e) {
                Log.d(TAG, e.toString());
                return false;
            }
        }
        return true;
    };


    //BluetoothDevice[] mDevice = {null, null};

    public boolean btStart(final int index) {
        //btStarting[0].set(false);
        //btStarting[1].set(false);


        Log.i(TAG, String.valueOf(index) + " " + BTAddress[index] + " btStarting...");
        if(btStarting[index].compareAndSet(false, true)) {

        }
        else {
            Log.w(TAG, String.valueOf(index) + " Connecting already running.");
            // Break loop
            return true;
        }


        BTStatus[index] = "CONNECTING " + String.valueOf(index) + ": " + BTAddress[index];
        if(mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, getBTStatusString() ).sendToTarget();

        // Restart streamServiceThread
        btCancel(index);

        if(!mBTAdapter.isEnabled()) {
            mBTAdapter.enable();
        }

        while (!mBTAdapter.isEnabled()) {
            // TODO:
        }

        if (BTAddress[index] == null) {
            //BTStatus = "SELECT BT DEVICE FIRST";
            //showNotification(BTStatus, null, 0);
            //if(mHandler != null)
            //    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();

            btStarting[index].set(false);
            serviceThreadRun[index] = false;
            Log.i(TAG, String.valueOf(index) + " BT device is not defined");
            return false;
        }

        BluetoothDevice mDevice = mBTAdapter.getRemoteDevice(BTAddress[index]);
        Log.i(TAG, String.valueOf(index) + " " + mDevice.toString());
        //mDevice[index] = mBTAdapter.getRemoteDevice(BTAddress[index]);

        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        try {
            if (mBTSocket[index] != null)  {
                mBTSocket[index].close();
                SystemClock.sleep(500);
            }
            mBTSocket[index] = createBluetoothSocket(mDevice);
        } catch (IOException e) {
            Log.e(TAG, String.valueOf(index) + " " + e.toString());
            btStarting[index].set(false);
            return false;
        }
        // Establish the Bluetooth socket connection.
        try {
            mBTSocket[index].connect();
        } catch (IOException e) {
            try {
                mBTSocket[index].close();
                //if(mHandler != null)
                //    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
            } catch (IOException e2) {
                Log.e(TAG, String.valueOf(index) + " " + e2.toString());
                btStarting[index].set(false);
                return false;
            }
            Log.e(TAG, String.valueOf(index) + " " + e.toString());
            btStarting[index].set(false);
            return false;
        }


        streamServiceThread[index] = new StreamServiceThread(index, mBTSocket[index]);
        streamServiceThread[index].start();

        BTStatus[index] = "CONNECTED " + String.valueOf(index) + ": " + BTAddress[index];
        showNotification(BTStatus[index], null, 0);
        if(mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, getBTStatusString() ).sendToTarget();

        new Thread() {
            SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
            String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + mPrefs.getString(Constants.PREFS_NAME_PATH, Constants.DEFAULT_PATH);
            String mTxFileName = Constants.DEFAULT_TX_FILENAME;

            // Let it continue running until it is stopped.
            public void run() {
                while (serviceThreadRun[index]) {
                    try {
                        //char[] buffer = new char[1024];  // Buffer store for the stream
                        byte[] buffer = new byte[1024];  // Buffer store for the stream
                        int bytes; // Bytes returned from read()
                        File file = new File(mPath + String.valueOf(index), mTxFileName);

                        if(file.exists()) {
                            FileInputStream fileInputStream = new FileInputStream(file);
                            //InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                            DataInputStream dataInputStream = new DataInputStream(fileInputStream);
                            //while ((bytes = inputStreamReader.read(buffer, 0, 1024)) > -1) {
                            while ((bytes = dataInputStream.read(buffer, 0, 1024)) > -1) {
                                //btSend(index, String.valueOf(buffer, 0, bytes));
                                //btSend(index, new String(buffer).getBytes(), 0, bytes);
                                btSend(index, buffer, 0, bytes);
                                //Log.d(TAG, String.valueOf(buffer, 0, bytes));



                                //String mTxFileName2 = "tmp.txt";//Constants.DEFAULT_TX_FILENAME;
                                //String mPath2 = Constants.DEFAULT_PATH;
                                //Utils.saveToFile(0, mPath2, mTxFileName2, buffer, bytes);

                            }

                            //inputStreamReader.close();
                            dataInputStream.close();
                            fileInputStream.close();

                            //TODO: Wait for data
                            if(waitForAnswer) {
                                SystemClock.sleep(250);
                            }

                            //while(mWaitForAnswer && !streamServiceThread.isRecieved) {
                            //    SystemClock.sleep(250);
                            //}
                            //Log.i(TAG, String.valueOf(streamServiceThread.isRecieved));
                            file.delete();
                        }

                    } catch (FileNotFoundException e) {
                        Log.d(TAG, String.valueOf(index) + e.getMessage());
                    }  catch(IOException e) {
                        Log.d(TAG, String.valueOf(index) + e.getMessage());
                    }
                    catch (Exception e) {
                        Log.d(TAG, String.valueOf(index) + e.getMessage());
                    }
                    SystemClock.sleep(250);
                }
            }
        }.start();

        Log.i(TAG, String.valueOf(index) + " btStarted");
        btStarting[index].set(false);
        return true;
    };

    private final BroadcastReceiver ActionFoundReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();

            // TODO:
            //Log.i(TAG, String.valueOf(index) + " " +context.toString());
            Log.i(TAG, intent.toString());

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(mAction)) {

                // TODO:
                //BTStatus = "CONNECTED";
                Log.i(TAG, getBTStatusString());

                //showNotification(BTStatus, null, 0);
                //if(mHandler != null)
                //    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();
                return;
            }
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(mAction)) {
                /*
                BTStatus = "DISCONNECTED " + BTAddress;
                showNotification(BTStatus, null, Notification.DEFAULT_SOUND);
                if(mHandler != null)
                    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus).sendToTarget();
                btCancel();
                new Thread() {
                    public void run() {
                        while (streamServiceThreadRun) {
                            if (btStart())
                                break;
                        }
                    }
                }.start();
                */

                // TODO:
                for(int i = 0 ; i < 7; i++){
                    if(streamServiceThread[i] == null || !streamServiceThread[i].isAlive()) {
                        if(BTAddress[i] != null) {
                            Connect(i);
                        }
                    }
                }
                return;
            }

            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    // Bluetooth was disconnected

                    // TODO:
                    for(int i = 0; i < 7; i++) {
                        if(BTAddress[i] != null) {
                            Connect(i);
                        }
                    }
                    return;
                }
            }

            //BTStatus = "STATE " + intent.toString();
            //showNotification(BTStatus, null, Notification.DEFAULT_SOUND);

        }
    };

    public boolean btSend(int index, byte[] buffer, int offset, int count) {
        if (streamServiceThread[index] != null) {
            if (streamServiceThread[index].isAlive()) {
                streamServiceThread[index].write(buffer, offset, count);
                return true;
            }
        }
        return false;
    }
    public boolean btSend(int index, String data){
        String s = new  String(data);
        return btSend(index, s.getBytes(), 0, s.getBytes().length);
        /*
        if (streamServiceThread[index] != null) {
            if (streamServiceThread[index].isAlive()) {
                streamServiceThread[index].write(data);
                return true;
            }
        }
        return false;
        */
    };

    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            //String pack = intent.getStringExtra("package");
            //String title = intent.getStringExtra("TITLE");
            //String text = intent.getStringExtra ("TEXT");

            /*
            String message = intent.getStringExtra  ("MESSAGE");

            if(message != null) {
                if (message.equals("AUTO_RECONNECT_ON")) {
                    autoReconnect = true;
                    return;
                }
                if (message.equals("AUTO_RECONNECT_OFF")) {
                    autoReconnect = false;
                    return;
                }

                if (!message.equals("")) {
                    btSend(message);
                }
            }
            */




            /*
            //int id = intent.getIntExtra("icon",0);
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

            } catch (Exception e) {
                e.printStackTrace();
            }
            */
        }
    };

    public String getBTStatusString() {
        String message = "";
        for (int i = 0; i < 7; i++) {
            if (BTStatus[i] != null) {
                if(message !=  "") {
                    message += "\n";
                }
                message += BTStatus[i];
            }
        }
        return message;
    };

    public void showNotification(String title, String message, int defaults) {

        //NotificationManager notificationManager;
        //notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationManager mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification mNotification = new Notification(R.drawable.ic_settings_input_component_black_24dp, "Notification", System.currentTimeMillis());

        mNotification.flags |= Notification.FLAG_NO_CLEAR;
        mNotification.defaults |= defaults;

        //Intent notificationIntent = new Intent(this, BluetoothSerialBridgeService.class);
        //PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);



        mNotification.setLatestEventInfo(this, title, message, pendingIntent);
        mNotificationManager.notify(1, mNotification);
    }
}

class StreamServiceThread extends Thread {

    final public static String TAG = BTSPP2FileService.class.getName();

    public final BluetoothSocket socket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    int index = -1;

    static boolean b0;

    public StreamServiceThread(int index, BluetoothSocket socket) {
        this.socket = socket;
        this.index = index;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        // Get the input and output streams, using temp objects because member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
        }

        mInStream = tmpIn;
        mOutStream = tmpOut;
    }

    public void run() {
        //SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        //String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + mPrefs.getString(Constants.PREFS_NAME_PATH, Constants.DEFAULT_PATH);
        String mPath = Constants.DEFAULT_PATH;
        String mTxFileName = Constants.DEFAULT_RX_FILENAME;

        byte[] buffer = new byte[1024];  // Buffer store for the stream
        int bytes; // Bytes returned from read()
        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read from the InputStream
                bytes = mInStream.read(buffer);

                //TODO: listener
                Utils.saveToFile(index, mPath, mTxFileName, buffer, bytes);

            } catch (IOException e) {
                Log.d(TAG, e.toString());
                break;
            }
            if(!socket.isConnected()) {
                Log.d(TAG, "Exiting StreamServiceThread thread.");
                break;
            }
        }
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(String input) {
        byte[] buffer = input.getBytes(); //Converts entered String into bytes
        write(buffer, 0, buffer.length);
    }

    public void write(byte[] buffer, int start, int bytes) {
        try {
            mOutStream.write(buffer, start, bytes);
        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            socket.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
    }
}