package be.ibad.deviceinfosample;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.Locale;

import be.ibad.deviceinfo.DeviceInfo;
import be.ibad.deviceinfo.DeviceTestingCategory;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView textView = (TextView) findViewById(R.id.device_info_text);

        textView.append(String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.DEVICE));
        textView.append(String.format("\n%s %s", DeviceInfo.getScreenSize(getApplicationContext()), DeviceInfo.getScreenResolution(this)));
        textView.append(String.format("\n%s %s", DeviceInfo.getDeviceDensity(getApplicationContext()), DeviceInfo.getScreenResolutionInDP(this)));
        textView.append("\n" + DeviceInfo.getAndroidVersionName());
        textView.append(String.format(Locale.US, "\nNb of CPU cores: %d", DeviceInfo.getNumberOfCPUCores()));
        textView.append("\n" + DeviceTestingCategory.getRamValue(getApplicationContext()));
        textView.append("\n" + DeviceTestingCategory.getClockSpeedValue());
        textView.append("\n\n" + DeviceTestingCategory.getDeviceClassInfo(getApplicationContext()));
        textView.append("\n\n Year class: " + DeviceTestingCategory.getYearClass(getApplicationContext()));
    }
}
