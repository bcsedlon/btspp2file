package cz.growmat.btspp2file;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by pravoj01 on 7.3.2018.
 */

public class Utils {

    final public static String TAG = Utils.class.getName();

    public static boolean saveToFile(String path, String fileName, byte[] data, int length){
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + path;
        try {
            new File(path).mkdir();
            File file = new File(path + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file,true);
            //fileOutputStream.write((data + System.getProperty("line.separator")).getBytes());
            fileOutputStream.write(data, 0, length);
            fileOutputStream.close();

            return true;

        }  catch(FileNotFoundException ex) {
            Log.w(TAG, ex.getMessage());
        }  catch(IOException ex) {
            Log.w(TAG, ex.getMessage());
        }
        return  false;
    }
}
