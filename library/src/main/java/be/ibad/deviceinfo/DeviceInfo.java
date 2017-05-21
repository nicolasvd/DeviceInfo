package be.ibad.deviceinfo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Helper class for accessing hardware specifications, including the number of CPU cores, CPU clock speed
 * and total available RAM.
 */
public class DeviceInfo {

    /**
     * The default return value of any method in this class when an
     * error occurs or when processing fails (Currently set to -1). Use this to check if
     * the information about the device in question was successfully obtained.
     */
    static final int DEVICEINFO_UNKNOWN = -1;
    private static final FileFilter CPU_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            String path = pathname.getName();
            //regex is slow, so checking char by char.
            if (path.startsWith("cpu")) {
                for (int i = 3; i < path.length(); i++) {
                    if (!Character.isDigit(path.charAt(i))) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    };

    /**
     * Reads the number of CPU cores from {@code /sys/devices/system/cpu/}.
     *
     * @return Number of CPU cores in the phone, or DEVICEINFO_UKNOWN = -1 in the event of an error.
     */
    public static int getNumberOfCPUCores() {
        int cores;
        try {
            cores = new File("/sys/devices/system/cpu/").listFiles(CPU_FILTER).length;
        } catch (SecurityException | NullPointerException e) {
            cores = DEVICEINFO_UNKNOWN;
        }
        return cores;
    }

    /**
     * Method for reading the clock speed of a CPU core on the device. Will read from either
     * {@code /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq} or {@code /proc/cpuinfo}.
     *
     * @return Clock speed of a core on the device, or -1 in the event of an error.
     */
    public static int getCPUMaxFreqKHz() {
        int maxFreq = DEVICEINFO_UNKNOWN;
        try {
            for (int i = 0; i < getNumberOfCPUCores(); i++) {
                String filename = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
                File cpuInfoMaxFreqFile = new File(filename);
                if (cpuInfoMaxFreqFile.exists()) {
                    byte[] buffer = new byte[128];
                    FileInputStream stream = new FileInputStream(cpuInfoMaxFreqFile);
                    try {
                        stream.read(buffer);
                        int endIndex = 0;
                        //Trim the first number out of the byte buffer.
                        while (Character.isDigit(buffer[endIndex]) && endIndex < buffer.length) {
                            endIndex++;
                        }
                        String str = new String(buffer, 0, endIndex);
                        Integer freqBound = Integer.parseInt(str);
                        if (freqBound > maxFreq) {
                            maxFreq = freqBound;
                        }
                    } catch (NumberFormatException e) {
                        //Fall through and use /proc/cpuinfo.
                    } finally {
                        stream.close();
                    }
                }
            }
            if (maxFreq == DEVICEINFO_UNKNOWN) {
                FileInputStream stream = new FileInputStream("/proc/cpuinfo");
                try {
                    int freqBound = parseFileForValue("cpu MHz", stream);
                    freqBound *= 1000; //MHz -> kHz
                    if (freqBound > maxFreq) maxFreq = freqBound;
                } finally {
                    stream.close();
                }
            }
        } catch (IOException e) {
            maxFreq = DEVICEINFO_UNKNOWN; //Fall through and return unknown.
        }
        return maxFreq;
    }

    /**
     * Calculates the total RAM of the device through Android API or /proc/meminfo.
     *
     * @param c - Context object for current running activity.
     * @return Total RAM that the device has, or DEVICEINFO_UNKNOWN = -1 in the event of an error.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static long getTotalMemory(Context c) {
        // memInfo.totalMem not supported in pre-Jelly Bean APIs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            am.getMemoryInfo(memInfo);
            return memInfo.totalMem;
        } else {
            long totalMem = DEVICEINFO_UNKNOWN;
            try {
                FileInputStream stream = new FileInputStream("/proc/meminfo");
                try {
                    totalMem = parseFileForValue("MemTotal", stream);
                    totalMem *= 1024;
                } finally {
                    stream.close();
                }
            } catch (IOException ignored) {
            }
            return totalMem;
        }
    }

    /**
     * Helper method for reading values from system files, using a minimised buffer.
     *
     * @param textToMatch - Text in the system files to read for.
     * @param stream      - FileInputStream of the system file being read from.
     * @return A numerical value following textToMatch in specified the system file.
     * -1 in the event of a failure.
     */
    private static int parseFileForValue(String textToMatch, FileInputStream stream) {
        byte[] buffer = new byte[1024];
        try {
            int length = stream.read(buffer);
            for (int i = 0; i < length; i++) {
                if (buffer[i] == '\n' || i == 0) {
                    if (buffer[i] == '\n') i++;
                    for (int j = i; j < length; j++) {
                        int textIndex = j - i;
                        //Text doesn't match query at some point.
                        if (buffer[j] != textToMatch.charAt(textIndex)) {
                            break;
                        }
                        //Text matches query here.
                        if (textIndex == textToMatch.length() - 1) {
                            return extractValue(buffer, j);
                        }
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            //Ignore any exceptions and fall through to return unknown value.
        }
        return DEVICEINFO_UNKNOWN;
    }

    /**
     * Helper method used by {@link #parseFileForValue(String, FileInputStream) parseFileForValue}. Parses
     * the next available number after the match in the file being read and returns it as an integer.
     *
     * @param index - The index in the buffer array to begin looking.
     * @return The next number on that line in the buffer, returned as an int. Returns
     * DEVICEINFO_UNKNOWN = -1 in the event that no more numbers exist on the same line.
     */
    private static int extractValue(byte[] buffer, int index) {
        while (index < buffer.length && buffer[index] != '\n') {
            if (Character.isDigit(buffer[index])) {
                int start = index;
                index++;
                while (index < buffer.length && Character.isDigit(buffer[index])) {
                    index++;
                }
                String str = new String(buffer, start, index - start);
                return Integer.parseInt(str);
            }
            index++;
        }
        return DEVICEINFO_UNKNOWN;
    }

    public static String getScreenSize(Context context) {
        int screenSize = context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

        String result;
        switch (screenSize) {
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                result = "XLarge";
                break;
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                result = "Large";
                break;
            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                result = "Normal";
                break;
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                result = "Small";
                break;
            default:
                result = "neither XLarge, large, normal or small";
        }
        return "Screen size: " + result;
    }

    public static String getDeviceDensity(Context context) {
        String result = "";
        float scale = context.getResources().getDisplayMetrics().density;
        if (scale < 1.0f) {
            result += "ldpi";
        } else if (scale < 1.5f) {
            result += "mdpi";
        } else if (scale < 2.0f) {
            result += "hdpi";
        } else if (scale < 3.0f) {
            result += "xhdpi";
        } else if (scale < 4.0f) {
            result += "xxhdpi";
        } else {
            result += "xxxhdpi";
        }
        return "Density: " + result;
    }

    public static String getAndroidVersionName() {

        switch (Build.VERSION.SDK_INT) {

            case Build.VERSION_CODES.BASE:
            case Build.VERSION_CODES.BASE_1_1:
                return "Android version: Base " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.CUPCAKE:
                return "Android version: Cupcake " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.DONUT:
                return "Android version: Donut " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.ECLAIR:
            case Build.VERSION_CODES.ECLAIR_0_1:
            case Build.VERSION_CODES.ECLAIR_MR1:
                return "Android version: Eclair " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.FROYO:
                return "Android version: Froyo " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.GINGERBREAD:
            case Build.VERSION_CODES.GINGERBREAD_MR1:
                return "Android version: Gingerbread " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.HONEYCOMB:
            case Build.VERSION_CODES.HONEYCOMB_MR1:
            case Build.VERSION_CODES.HONEYCOMB_MR2:
                return "Android version: Honeycomb " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH:
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
                return "Android version: Ice Cream Sandwich " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.JELLY_BEAN:
            case Build.VERSION_CODES.JELLY_BEAN_MR1:
            case Build.VERSION_CODES.JELLY_BEAN_MR2:
                return "Android version: Jelly Bean " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.KITKAT:
                return "Android version: KitKat " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.LOLLIPOP:
            case Build.VERSION_CODES.LOLLIPOP_MR1:
                return "Android version: Lollipop " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.M:
                return "Android version: Marshmallow " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.N:
            case Build.VERSION_CODES.N_MR1:
                return "Android version: Nougat " + Build.VERSION.RELEASE;
            case Build.VERSION_CODES.CUR_DEVELOPMENT:
                return "Android version: Development " + Build.VERSION.RELEASE;
        }
        return "Android version unknown SDK number: " + Build.VERSION.SDK_INT + " " + Build.VERSION.RELEASE;
    }

    public static String getScreenResolution(Activity activity) {
        int measuredWidth;
        int measuredHeight;
        WindowManager w = activity.getWindowManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            w.getDefaultDisplay().getSize(size);
            measuredWidth = size.x;
            measuredHeight = size.y;
        } else {
            Display d = w.getDefaultDisplay();
            measuredWidth = d.getWidth();
            measuredHeight = d.getHeight();
        }

        return "(" + measuredWidth + "x" + measuredHeight + ")px";
    }

    public static String getScreenResolutionInDP(Activity activity) {
        int measuredWidth;
        int measuredHeight;
        WindowManager w = activity.getWindowManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            w.getDefaultDisplay().getSize(size);
            measuredWidth = size.x;
            measuredHeight = size.y;
        } else {
            Display d = w.getDefaultDisplay();
            measuredWidth = d.getWidth();
            measuredHeight = d.getHeight();
        }

        return "(" + convertPixelsToDp(measuredWidth, activity) + "x" + convertPixelsToDp(measuredHeight, activity) + ")dp";
    }

    private static int convertPixelsToDp(float px, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (int) (px / (metrics.densityDpi / 160f));
    }
}