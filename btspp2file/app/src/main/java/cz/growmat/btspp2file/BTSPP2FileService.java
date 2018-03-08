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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import cz.growmat.btspp2file.R;

public class BTSPP2FileService extends Service {

    final public static String TAG = BTSPP2FileService.class.getName();

    public StreamServiceThread streamServiceThread;
    public boolean streamServiceThreadRun = true;

    private static BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private static BluetoothAdapter mBTAdapter = null;
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    public static String BTAddress;
    public String BTStatus;

    // Defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    public Handler mHandler; // Our main handler that will receive callback notifications



    public static boolean autoTest = false;
    private boolean mAutoTestState = false;
    public void startAutoTest() {
        new Thread() {
            public void run() {

                while(autoTest) {
                    SystemClock.sleep(250);
                    //if(mServer != null) {
                        if (mAutoTestState) {
                            btSend("d10,1;");
                            btSend("d9,0;");
                        } else {
                            btSend("d10,0;");
                            btSend("d9,1;");
                        }
                        mAutoTestState = !mAutoTestState;
                    //}
                }
            }
        }.start();
    }

    public void Connect(String BTAddress) {

        showNotification("CONNECTING " + BTAddress, null, 0);

        this.BTAddress = BTAddress;
        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        SharedPreferences.Editor mEditor = mPrefs.edit();
        mEditor.putString(Constants.PREFS_NAME_BT_ADDRESS, BTAddress).commit();

        btCancel();

        streamServiceThreadRun = true;
        new Thread() {
            public void run() {
                while (streamServiceThreadRun) {
                    if (btStart())
                        break;
                }
            }
        }.start();
        //btStart();
    }

    public void Reconnect() {
        BTStatus = "DISCONNECTED " + BTAddress;
        showNotification(BTStatus, null, Notification.DEFAULT_SOUND);
        if (mHandler != null)
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
        return;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    final static String fileName2 = "log.txt";

    @Override
    public void onCreate() {

        showNotification("BLUETOOTH SERIAL BRIDGE", null, 0);

        /*
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag");
        wl.acquire();
        */

        //LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.d(TAG, "Service Created");
    }

    PowerManager.WakeLock wl;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        wl.acquire();

        Log.d(TAG, "Service Starting...");

        Time now = new Time();
        now.setToNow();
        String s = now.toString() + '\n';
        Utils.saveToFile(Constants.DEFAULT_PATH, Constants.DEFAULT_LOG_FILENAME, s.getBytes(), s.getBytes().length);

        SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
        BTAddress = mPrefs.getString(Constants.PREFS_NAME_BT_ADDRESS, null);

        //mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        new Thread() {
            public void run() {
                while (streamServiceThreadRun) {
                    if (btStart())
                        break;
                }
            }
        }.start();

        new Thread() {
            // Let it continue running until it is stopped.
            public void run() {
                SharedPreferences mPrefs = getSharedPreferences(Constants.PREFS_NAME, 0);
                String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + mPrefs.getString(Constants.PREFS_NAME_PATH, Constants.DEFAULT_PATH);
                String mTxFileName = Constants.DEFAULT_TX_FILENAME;
                boolean mWaitForAnswer = mPrefs.getBoolean(Constants.PREFS_NAME_WAIT_FOR_ANSWER, false);

                while (streamServiceThreadRun) {
                    try {


                        char[] buffer = new char[1024];  // Buffer store for the stream
                        int bytes; // Bytes returned from read()
                        File file = new File(mPath + mTxFileName);

                        if(file.exists()) {
                            FileInputStream fileInputStream = new FileInputStream(file);
                            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);



                            while ((bytes = inputStreamReader.read(buffer, 0, 1024)) > -1) {
                                btSend(String.valueOf(buffer, 0, bytes));
                                Log.d(TAG, String.valueOf(buffer, 0, bytes));
                            }

                            inputStreamReader.close();
                            fileInputStream.close();

                            //TODO: Wait for data
                            if(mWaitForAnswer) {
                                SystemClock.sleep(250);
                            }

                            //while(mWaitForAnswer && !streamServiceThread.isRecieved) {
                            //    SystemClock.sleep(250);
                            //}
                            //Log.i(TAG, String.valueOf(streamServiceThread.isRecieved));
                            file.delete();
                        }

                    } catch (FileNotFoundException e) {
                        Log.d(TAG, e.getMessage());
                    }  catch(IOException e) {
                        Log.d(TAG, e.getMessage());
                    }
                    catch (Exception e) {
                        Log.d(TAG, e.getMessage());
                    }
                    SystemClock.sleep(100);
                }
            }
        }.start();

        autoTest = mPrefs.getBoolean(Constants.PREFS_NAME_AUTO_TEST, true);
        if(autoTest)
            startAutoTest();

        Log.d(TAG, "Service Started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        wl.release();
        btCancel();
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

    public boolean btCancel(){
        if(streamServiceThread != null) {
            streamServiceThread.cancel();
            streamServiceThread = null;
            try{
                mBTSocket.close();
            } catch (IOException e) {
                Log.d(TAG, e.toString());
                return false;
            }
        }
        return true;
    };

    public boolean btStart() {

        BTStatus = "CONNECTING " + BTAddress;
        if(mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();

        // Restart streamServiceThread
        btCancel();

        if(!mBTAdapter.isEnabled()) {
            mBTAdapter.enable();
        }

        while (!mBTAdapter.isEnabled()) {
            //TODO:
        }

        if (BTAddress == null) {
            BTStatus = "SELECT BT DEVICE FIRST";
            showNotification(BTStatus, null, 0);
            if(mHandler != null)
                mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();

            Log.d(TAG, "BT device is not defined");

            streamServiceThreadRun = false;
            return false;
        }

        BluetoothDevice mDevice = mBTAdapter.getRemoteDevice(BTAddress);

        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        getApplicationContext().registerReceiver(ActionFoundReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        try {
            if (mBTSocket != null)  {
                mBTSocket.close();
                SystemClock.sleep(500);
            }
            mBTSocket = createBluetoothSocket(mDevice);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return false;
        }
        // Establish the Bluetooth socket connection.
        try {
            mBTSocket.connect();
        } catch (IOException e) {
            try {
                mBTSocket.close();
                //if(mHandler != null)
                //    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
            } catch (IOException e2) {
                Log.e(TAG, e2.toString());
                return false;
            }
            return false;
        }

        streamServiceThread = new StreamServiceThread(mBTSocket);
        streamServiceThread.start();

        BTStatus = "CONNECTED " + BTAddress;
        showNotification(BTStatus, null, 0);
        if(mHandler != null)
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();

        return true;
    };

    private final BroadcastReceiver ActionFoundReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(mAction)) {

                BTStatus = "CONNECTED " + BTAddress;
                Log.i(TAG, BTStatus);

                showNotification(BTStatus, null, 0);
                if(mHandler != null)
                    mHandler.obtainMessage(CONNECTING_STATUS, -1, -1, BTStatus ).sendToTarget();
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
                Reconnect();
                return;
            }

            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    // Bluetooth was disconnected
                    Reconnect();
                    return;
                }
            }

            //BTStatus = "STATE " + intent.toString();
            //showNotification(BTStatus, null, Notification.DEFAULT_SOUND);

        }
    };

    public boolean btSend(String data){
        if(streamServiceThread != null) {
            if(streamServiceThread.isAlive())  {
                streamServiceThread.write(data);
                return true;
            }
        }
        return false;
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

    static boolean b0;

    public StreamServiceThread(BluetoothSocket socket) {
        this.socket = socket;
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
                Utils.saveToFile(mPath, mTxFileName, buffer, bytes);

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