package com.gen.mavsdkandroidserialtest.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.gen.mavsdkandroidserialtest.models.PositionRelative;
import com.gen.mavsdkandroidserialtest.models.Speed;
import com.gen.mavsdkandroidserialtest.repositories.DroneRepository;

import io.mavsdk.telemetry.Telemetry;

public class MainActivityViewModel extends AndroidViewModel {

    private DroneRepository mDroneRepository;

    public MainActivityViewModel(@NonNull Application application) {
        super(application);
        mDroneRepository = DroneRepository.getInstance(application);
    }

    public DroneRepository getDroneRepositoryInstance() {
        return mDroneRepository;
    }

    public LiveData<PositionRelative> getPositionRelative() {
        return mDroneRepository.getPositionRelative();
    }

    public LiveData<Speed> getSpeed() {
        return mDroneRepository.getSpeed();
    }

    public LiveData<Telemetry.Battery> getBattery() {
        return mDroneRepository.getBattery();
    }

    public LiveData<Telemetry.GpsInfo> getGpsInfo() {
        return mDroneRepository.getGpsInfo();
    }

    public LiveData<Telemetry.Position> getPosition() {
        return mDroneRepository.getPosition();
    }
}
