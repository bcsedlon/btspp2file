package cz.growmat.btspp2file;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Created by pravoj01 on 7.3.2018.
 */

public class Utils {

    final public static String TAG = Utils.class.getName();
    public static OutputStreamWriter[] outputStreamWriters = {null, null, null, null, null, null, null};


    public static boolean saveToFile(int index, String path, String fileName, byte[] data, int length) {
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + path;
        try {
            new File(path).mkdir();

            File file;
            if (index < 0) {
                file = new File(path, fileName);
            } else {
                new File(path, String.valueOf(index)).mkdir();
                file = new File(path + String.valueOf(index), fileName);
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            //fileOutputStream.write((data + System.getProperty("line.separator")).getBytes());
            fileOutputStream.write(data, 0, length);
            fileOutputStream.close();

            return true;

        } catch (FileNotFoundException ex) {
            Log.w(TAG, ex.getMessage());
        } catch (IOException ex) {
            Log.w(TAG, ex.getMessage());
        }
        return false;
    }

    public static OutputStreamWriter createPipe(String path, String pipeName){
        OutputStreamWriter outputStreamWriter = null;
        try {
            new File(path).mkdir();

            if (outputStreamWriter == null) {
                new File(path).mkdir();
                outputStreamWriter = new OutputStreamWriter(new FileOutputStream(path + "/" + pipeName, false));
                outputStreamWriter.close();
            }
            return outputStreamWriter;
        }   catch(FileNotFoundException ex){
            Log.w(TAG, ex.getMessage());
        }  catch(IOException ex){
            Log.w(TAG, ex.getMessage());
        }
        return null;
    }

    public static boolean writeToPipe(int index, String path, String pipeName, char[] data, int length){
        // create folder 0, 1, 2, 3, 4, 5, 6 in /data/data/cz.growmat.btspp2file/ an in each folder
        // create manually pipes mknod tx p
        // create manually pipes mknod rx p

        //path = Environment.getExternalStorageDirectory().getAbsolutePath() + path;
        String mPath = path + String.valueOf(index) + "/";

        OutputStreamWriter outputStreamWriter = outputStreamWriters[index];
        try {


            if(outputStreamWriter == null) {
                //Log.i(TAG, String.valueOf(index) + " creating output pipe...");
                //outputStreamWriters[index] = createPipe(path, pipeName);

                //TODO: create input pipe
                //new File(path).mkdir();
                //outputStreamWriter = new OutputStreamWriter(new FileOutputStream(path + String.valueOf(index) + "/rx", false));
                //outputStreamWriter.close();
                Log.i(TAG, String.valueOf(index) + " opening output pipe... " + mPath + pipeName);

                outputStreamWriter = new OutputStreamWriter(new FileOutputStream(mPath + pipeName, false));
                //outputStreamWriter = new OutputStreamWriter(new FileOutputStream(pipeName));
                outputStreamWriters[index] = outputStreamWriter;

            }

            outputStreamWriter.write(data, 0, length);
            outputStreamWriter.flush();
            return true;
            //outputStreamWriter.flush();
            //Log.v(TAG, "Closing…");
            //out.close();
        }  catch(FileNotFoundException ex) {
            Log.w(TAG, ex.getMessage());
        }  catch(IOException ex) {
            Log.w(TAG, ex.getMessage());
        }
        return false;
    }

    public static boolean closePipes() {
        for(int index = 0 ; index < 7; index++) {
            try {
                if (outputStreamWriters[index] != null) {
                    outputStreamWriters[index].close();
                    outputStreamWriters[index] = null;
                }
            } catch (Exception ex) {
                Log.w(TAG, ex.getMessage());
            }
        }
        return true;
    }
}
