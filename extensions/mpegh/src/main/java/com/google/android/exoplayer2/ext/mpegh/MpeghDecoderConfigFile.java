package com.google.android.exoplayer2.ext.mpegh;

import android.util.Log;
import android.content.Context;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.io.FileInputStream;
import java.io.FileOutputStream;

//--------------------------------------------------------------------//
// IA decoder impl
//--------------------------------------------------------------------//

/**
 * MpeghDecoderConfigFile A class managing config files for passing
 * coefficient files to Mpegh decoder. It manages config file paths
 * and coefficient file paths in the device.
 */
public class MpeghDecoderConfigFile {
    static private String TAG = "MpeghDecoderConfigFile";

    /**
     * MpeghCoefType Coefficiend types for IA decoder API.
     */
    public enum CoefType {
        Hrtf13("hrtf13"),
        Cp("cp"), ;

        private final String str;

        private CoefType(final String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return this.str;
        }
    }

    /**
     * Get application root path: the app specific region in the internal storage.
     * @param context Current context which holds information of this application.
     */
    static public String getAppRootPath(Context context) {
        String path = context.getFilesDir().getParent();
        return path;
    }

    /**
     * An application root path
     * (ex) /data/user/0/com.google.android.exoplayer2.demo/files/
     */
    private String appRootpath = "";

    /**
     * A relative root path from the application root path.
     * (ex) com.sony.immersive-audio/coef/
     */
    private String relativeRootPath = kRelativeFileDir;

    /**
     * Default relative root path
     */
    static private String kRelativeFileDir = "files/com.sony.immersive-audio/coef/";
    /**
     * Default HRTF config file name
     */
    static private String kHrtfConfigFileName = "com.sony.360ra.hrtf13.config";
    /**
     * Default CP config file name
     */
    static private String kCpConfigFileName = "com.sony.360ra.cp.config";

    /**
     * Constructor
     * @param appRootpath An application root path. Typically it can be obtained by
     *                    context.getFilesDir().getParent().
     */
    public MpeghDecoderConfigFile(String appRootpath) {
        this.appRootpath = appRootpath + "/";
        printInfo();
    }

    public void printInfo() {
        Log.d(TAG, "App root path: " + appRootpath);
        Log.d(TAG, "Relative root path: " + relativeRootPath);

        Log.d(TAG, "Absolute HRTF config filepath: " + getAbsoluteConfigFilePath(CoefType.Hrtf13));
        Log.d(TAG, "Absolute CP config filepath: " + getAbsoluteConfigFilePath(CoefType.Cp));
        Log.d(TAG, "Relative HRTF config filepath: " + getRelativeConfigFilePath(CoefType.Hrtf13));
        Log.d(TAG, "Relative CP config filepath: " + getRelativeConfigFilePath(CoefType.Cp));
    }

    /**
     * Get get config file name
     */
    public String getConfigFileName(CoefType type) {
        switch (type) {
            case Hrtf13:
                return kHrtfConfigFileName;
            case Cp:
                return kCpConfigFileName;
        }
        return "";
    }


    /**
     * Get get relative root path
     */
    public String getRelativeRootPath() {
        String path = relativeRootPath;
        return path;
    }

    /**
     * Get get absolute path of config file
     * (ex) /data/user/0/com.google.android.exoplayer2.demo/files/
     *       com.sony.immersive-audio/coef/com.sony.360ra.hrtf13.config
     */
    public String getAbsoluteConfigFilePath(CoefType type) {
        String path = this.appRootpath + getRelativeConfigFilePath(type);
        return path;
    }

    /**
     * Get get relative path of config file
     * (ex) com.sony.immersive-audio/coef/com.sony.360ra.hrtf13.config
     */
    public String getRelativeConfigFilePath(CoefType type) {
        String path = relativeRootPath + getConfigFileName(type);
        return path;
    }


    /**
     * Read path stored in the current config file.
     */
    public String readConfigFile(String path) {
        String str = "";
        try{
            File file = new File(path);
            BufferedReader bufReader = new BufferedReader(new FileReader(file));
            str = bufReader.readLine();
            bufReader.close();
            return str;
        }catch(FileNotFoundException e){
            System.out.println(e);
        }catch(IOException e){
            System.out.println(e);
        }
        return str;
    }

    /**
     * Read coefficient file path stored in the current config file.
     */
    public String readRelativeCoefFilePath(CoefType type) {
        String path = getAbsoluteConfigFilePath(type);
        return readConfigFile(path);
    }

    /**
     * Read absolute coefficient file path stored in the current config file.
     */
    public String readAbsoluteCoefFilePath(CoefType type) {
        String path = getAbsoluteConfigFilePath(type);
        String relativeFilePath = readConfigFile(path);
        return this.appRootpath + relativeFilePath;
    }

    /**
     * Check specified config file exists.
     */
    public boolean isExistConfigFile(CoefType type) {
        File file = new File(getAbsoluteConfigFilePath(type));
        return file.exists();
    }

}
