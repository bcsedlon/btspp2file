package cz.growmat.btspp2file;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import static android.content.Context.BIND_AUTO_CREATE;

public class DeviceBootReceiver extends BroadcastReceiver {

    //final public static String ONE_TIME = "onetime";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {

            /*
            // Setting the alarm here
            Intent alarmIntent = new Intent(context, AlarmManagerBroadcastReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            int interval = 8000;
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, pendingIntent);
            Toast.makeText(context, "Alarm Set", Toast.LENGTH_SHORT).show();
            */
            //Intent mIntent = new Intent(this, BTSPP2FileService.class);
            //mIntent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
            Intent serviceIntent = new Intent(context, BTSPP2FileService.class);
            context.startService(serviceIntent);
        }
    }

    public int getComponentEnabledSetting(Context context) {
        ComponentName componentName = new ComponentName(context, DeviceBootReceiver.class);
        PackageManager pm = context.getPackageManager();
        return pm.getComponentEnabledSetting(componentName);
    }

    public void enable(Context context) {
        ComponentName receiver = new ComponentName(context, DeviceBootReceiver.class);
        context.getPackageManager().setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public void disable(Context context) {
        ComponentName receiver = new ComponentName(context, DeviceBootReceiver.class);
        context.getPackageManager().setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
