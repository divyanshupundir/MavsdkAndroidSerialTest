package com.gen.mavsdkandroidserialtest.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.gen.mavsdkandroidserialtest.R;
import com.gen.mavsdkandroidserialtest.models.PositionRelative;
import com.gen.mavsdkandroidserialtest.repositories.DroneRepository;
import com.gen.mavsdkandroidserialtest.utils.TextUtils;
import com.gen.mavsdkandroidserialtest.viewmodels.MainActivityViewModel;

import java.util.ArrayList;
import java.util.List;

import io.mavsdk.telemetry.Telemetry;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LOG_" + MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_ALL_PERMISSIONS = 1001;
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private MainActivityViewModel mViewModel;

    private TextView tv_main_data_distance;
    private TextView tv_main_data_height;
    private TextView tv_main_data_battery_charge;
    private TextView tv_main_data_battery_voltage;
    private TextView tv_main_data_latitude;
    private TextView tv_main_data_longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_main_data_distance = findViewById(R.id.tv_main_data_distance);
        tv_main_data_height = findViewById(R.id.tv_main_data_height);
        tv_main_data_battery_charge = findViewById(R.id.tv_main_data_battery_charge);
        tv_main_data_battery_voltage = findViewById(R.id.tv_main_data_battery_voltage);
        tv_main_data_latitude = findViewById(R.id.tv_main_data_latitude);
        tv_main_data_longitude = findViewById(R.id.tv_main_data_longitude);

        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel.class);

        if (checkPermissions()) {
            observeData();
        }
    }

    private void observeData() {
        mViewModel.getPositionRelative().observe(this, positionRelative -> {
            tv_main_data_distance.setText(TextUtils.roundToDecimalPlaces(positionRelative.getDistance(), 2));
            tv_main_data_height.setText(TextUtils.roundToDecimalPlaces(positionRelative.getHeight(), 2));
        });

        mViewModel.getBattery().observe(this, battery -> {
            int remainingPercentInt = (int) (battery.getRemainingPercent() * 100);

            tv_main_data_battery_charge.setText(String.valueOf(remainingPercentInt).concat("%"));
            tv_main_data_battery_voltage.setText(TextUtils.roundToDecimalPlaces(battery.getVoltageV(), 1));
        });

        mViewModel.getPosition().observe(this, position -> {
            tv_main_data_latitude.setText(TextUtils.roundToDecimalPlaces(position.getLatitudeDeg(), 5));
            tv_main_data_longitude.setText(TextUtils.roundToDecimalPlaces(position.getLongitudeDeg(), 5));

        });
    }

    private boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : PERMISSIONS) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), REQUEST_CODE_ALL_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_ALL_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                observeData();
            }
        }
    }

    @Override
    protected void onDestroy() {
        mViewModel.getDroneRepositoryInstance().destroy();
        super.onDestroy();
    }
}