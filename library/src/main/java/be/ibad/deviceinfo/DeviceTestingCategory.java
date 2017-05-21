package be.ibad.deviceinfo;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;


public class DeviceTestingCategory {

    private static final String TAG = "DeviceTestingCategory";

    // Year definitions
    private static final int CLASS_UNKNOWN = -1;
    private static final int CLASS_2008 = 2008;
    private static final int CLASS_2009 = 2009;
    private static final int CLASS_2010 = 2010;
    private static final int CLASS_2011 = 2011;
    private static final int CLASS_2012 = 2012;
    private static final int CLASS_2013 = 2013;
    private static final int CLASS_2014 = 2014;
    private static final int CLASS_2015 = 2015;
    private static final int CLASS_2016 = 2016;
    private static final int CLASS_2017 = 2017;

    private static final String CLASS_LOW = "LOW";
    private static final String CLASS_MEDIUM = "MEDIUM";
    private static final String CLASS_HIGH = "HIGH";

    private static final long MB = 1024 * 1024;
    private static final int MHZ_IN_KHZ = 1000;

    private volatile static Integer mYearCategory;
    private volatile static String mClassCategory;


    /**
     * Entry Point of DeviceTestingCategory. Extracts DeviceTestingCategory variable with memoizing.
     * Example usage:
     * <p/>
     * <pre>
     *   String deviceClassInfo = DeviceTestingCategory.getDeviceClassInfo(context);
     * </pre>
     */
    public static String getDeviceClassInfo(Context c) {
        String formattedReturn = "Device category: %s";
        if (mYearCategory == null) {
            synchronized (DeviceTestingCategory.class) {
                if (mYearCategory == null) {
                    mYearCategory = categorizeByYear(c);
                    if (mYearCategory == CLASS_2008) mClassCategory = CLASS_LOW;
                    if (mYearCategory == CLASS_2009) mClassCategory = CLASS_LOW;
                    if (mYearCategory == CLASS_2010) mClassCategory = CLASS_LOW;
                    if (mYearCategory == CLASS_2011) mClassCategory = CLASS_LOW;
                    if (mYearCategory == CLASS_2012) mClassCategory = CLASS_MEDIUM;
                    if (mYearCategory == CLASS_2013) mClassCategory = CLASS_MEDIUM;
                    if (mYearCategory == CLASS_2014) mClassCategory = CLASS_MEDIUM;
                    if (mYearCategory == CLASS_2015) mClassCategory = CLASS_HIGH;
                    if (mYearCategory == CLASS_2016) mClassCategory = CLASS_HIGH;
                    if (mYearCategory == CLASS_2017) mClassCategory = CLASS_HIGH;
                    return String.format(Locale.US, formattedReturn, mClassCategory);
                }
            }
        }
        return String.format(Locale.US, formattedReturn, mClassCategory);
    }

    /**
     * Entry Point of DeviceTestingCategory. Extracts DeviceYearClass variable with memoizing.
     * Example usage:
     * <p/>
     * <pre>
     *   int yearClass = YearClass.getYearClass(context);
     * </pre>
     */
    public static int getYearClass(Context c) {
        if (mYearCategory == null) {
            synchronized (DeviceTestingCategory.class) {
                if (mYearCategory == null) {
                    mYearCategory = categorizeByYear(c);
                }
            }
        }
        return mYearCategory;
    }

    private static void conditionallyAdd(ArrayList<Integer> list, int value) {
        if (value != CLASS_UNKNOWN) {
            list.add(value);
        }
    }

    /**
     * Calculates the "best-in-class year" of the device. This represents the top-end or flagship
     * devices of that year, not the actual release year of the phone. For example, the Galaxy Duos
     * S was released in 2012, but its specs are very similar to the Galaxy S that was released in
     * 2010 as a then top-of-the-line phone, so it is a 2010 device.
     *
     * @return The year when this device would have been considered top-of-the-line.
     */
    private static int categorizeByYear(Context c) {
        Log.v(TAG, "getClockSpeedYear(): " + getClockSpeedYear());
        Log.v(TAG, "getNumCoresYear(): " + getNumCoresYear());
        Log.v(TAG, "getRamYear(): " + getRamYear(c));

        ArrayList<Integer> componentYears = new ArrayList<>();
        conditionallyAdd(componentYears, getClockSpeedYear());
        conditionallyAdd(componentYears, getRamYear(c));

        if (componentYears.isEmpty()) {
            // GKB: Fallback to using number of cores only if nothing else is available.
            conditionallyAdd(componentYears, getNumCoresYear());
        }

        // GKB: Simplified derivation of overall device year by taking average of individual years.
        int avgYear = 0;
        for (Integer year : componentYears) {
            if (year > CLASS_UNKNOWN) {
                avgYear += year;
            }
        }

        return (avgYear > 0) ?
                (avgYear / componentYears.size()) : // GKB: Implies floor() function via division operation.
                CLASS_UNKNOWN;
    }

    /**
     * Calculates the year class by the number of processor cores the phone has.
     *
     * @return the year in which top-of-the-line phones had the same number of processors as this phone.
     */
    private static int getNumCoresYear() {
        int cores = DeviceInfo.getNumberOfCPUCores();
        if (cores < 1) return CLASS_UNKNOWN;
        if (cores == 1) return CLASS_2008;
        if (cores <= 3) return CLASS_2015;
        if (cores <= 4) return CLASS_2016;
        return CLASS_2017; // E.g. Octa core devices
    }

    /**
     * Calculates the year class by the clock speed of the cores in the phone.
     *
     * @return the year in which top-of-the-line phones had the same clock speed.
     */
    private static int getClockSpeedYear() {
        long clockSpeedKHz = DeviceInfo.getCPUMaxFreqKHz();
        if (clockSpeedKHz == DeviceInfo.DEVICEINFO_UNKNOWN) return CLASS_UNKNOWN;

        // GKB: Clock speed dropped when core count was upped to 8 so factor this into the calc.
        int cores = DeviceInfo.getNumberOfCPUCores();
        if (cores < 8) {
            // These cut-offs include 20MHz of "slop" because my "1.5GHz" Galaxy S3 reports
            // its clock speed as 1512000. So we add a little slop to keep things nominally correct.
            if (clockSpeedKHz <= 528 * MHZ_IN_KHZ) return CLASS_2008;
            if (clockSpeedKHz <= 620 * MHZ_IN_KHZ) return CLASS_2009;
            if (clockSpeedKHz <= 1020 * MHZ_IN_KHZ) return CLASS_2010;
            if (clockSpeedKHz <= 1220 * MHZ_IN_KHZ) return CLASS_2011;
            if (clockSpeedKHz <= 1520 * MHZ_IN_KHZ) return CLASS_2012;
            if (clockSpeedKHz <= 2020 * MHZ_IN_KHZ) return CLASS_2014;
            if (clockSpeedKHz <= 2200 * MHZ_IN_KHZ) return CLASS_2016;
            return CLASS_2017;
        } else {
            if (clockSpeedKHz <= 1520 * MHZ_IN_KHZ) return CLASS_2015;
            return CLASS_2016;
        }
    }

    public static String getClockSpeedValue() {
        long clockSpeedKHz = DeviceInfo.getCPUMaxFreqKHz();
        return "Clock speed: " + clockSpeedKHz / MHZ_IN_KHZ + " Mhz";
    }

    //TODO add 64 bits check
    public static boolean is64bitsCPU() {
        return Build.SUPPORTED_64_BIT_ABIS.length > 0;
    }

    /**
     * Calculates the year class by the amount of RAM the phone has.
     *
     * @return the year in which top-of-the-line phones had the same amount of RAM as this phone.
     */
    private static int getRamYear(Context c) {
        long totalRam = DeviceInfo.getTotalMemory(c);
        if (totalRam <= 0) return CLASS_UNKNOWN;
        if (totalRam <= 192 * MB) return CLASS_2008;
        if (totalRam <= 290 * MB) return CLASS_2009;
        if (totalRam <= 512 * MB) return CLASS_2010;
        if (totalRam <= 1024 * MB) return CLASS_2011;
        if (totalRam <= 1536 * MB) return CLASS_2012;
        if (totalRam <= 2048 * MB) return CLASS_2015;
        if (totalRam <= 4096 * MB) return CLASS_2016;
        return CLASS_2017;
    }

    public static String getRamValue(Context c) {
        long totalRam = DeviceInfo.getTotalMemory(c);
        return "Total Ram: " + totalRam / MB + " Mb";
    }
}