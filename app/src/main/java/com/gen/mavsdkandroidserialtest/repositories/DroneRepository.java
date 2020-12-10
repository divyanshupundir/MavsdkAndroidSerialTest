package com.gen.mavsdkandroidserialtest.repositories;

import android.app.Application;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;

import com.gen.mavsdkandroidserialtest.io.TcpInputOutputManager;
import com.gen.mavsdkandroidserialtest.models.PositionRelative;
import com.gen.mavsdkandroidserialtest.models.Speed;
import com.google.common.collect.Lists;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.mavsdk.System;
import io.mavsdk.mavsdkserver.MavsdkServer;
import io.mavsdk.telemetry.Telemetry;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DroneRepository {
    private static final String TAG = "LOG_" + DroneRepository.class.getSimpleName();
    private static final boolean GENERATE_DUMMY_DATA = false;
    private static final boolean IS_SITL = false;

    private static final String MAVSDK_SERVER_IP = "127.0.0.1";
    private static final int TCP_SERVER_PORT = 8888;
    private static final int USB_BAUD_RATE = 57600;
    private static final int BUFFER_SIZE = 2048;
    private static final int IO_TIMEOUT = 1000;
    private static final long THROTTLE_TIME_MILLIS = 500;

    private static DroneRepository instance;

    private final Context mAppContext;
    private final CompositeDisposable mCompositeDisposable;
    private System mDrone;
    private MavsdkServer mMavsdkServer;
    private SerialInputOutputManager mSerialManager;
    private TcpInputOutputManager mTcpManager;

    private LiveData<PositionRelative> mPositionRelativeLiveData;
    private LiveData<Speed> mSpeedLiveData;
    private LiveData<Telemetry.Battery> mBatteryLiveData;
    private LiveData<Telemetry.GpsInfo> mGpsInfoLiveData;
    private LiveData<Telemetry.Position> mPositionLiveData;

    public static DroneRepository getInstance(@NonNull Application application) {
        if (instance == null) {
            instance = new DroneRepository(application);
        }
        return instance;
    }

    private DroneRepository(Application application) {
        mAppContext = application.getApplicationContext();
        mCompositeDisposable = new CompositeDisposable();

        if (IS_SITL) {
            initializeServerAndDrone("udp://:14540"); // Default port
        } else {
            initializeUsbAndTcp();
            initializeServerAndDrone("tcp://:" + TCP_SERVER_PORT);
        }
    }

    private void initializeUsbAndTcp() {
        UsbManager usbManager = (UsbManager) mAppContext.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> driverList = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (driverList.isEmpty()) {
            return;
        }

        UsbSerialDriver usbSerialDriver = driverList.get(0);
        UsbDevice usbDevice = usbSerialDriver.getDevice();
        Log.d(TAG, "initializeUsbDevice: hasPermission: " + usbManager.hasPermission(usbDevice));

        UsbSerialPort usbSerialPort = usbSerialDriver.getPorts().get(0);
        UsbDeviceConnection connection = usbManager.openDevice(usbDevice);

        try {
            usbSerialPort.open(connection);
            usbSerialPort.setParameters(
                    USB_BAUD_RATE,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mSerialManager = new SerialInputOutputManager(usbSerialPort);
        mSerialManager.setReadTimeout(IO_TIMEOUT);
        mSerialManager.setReadBufferSize(BUFFER_SIZE);
        mSerialManager.setWriteTimeout(IO_TIMEOUT);
        mSerialManager.setWriteBufferSize(BUFFER_SIZE);

        mTcpManager = new TcpInputOutputManager(TCP_SERVER_PORT);
        mTcpManager.setReadBufferSize(BUFFER_SIZE);
        mTcpManager.setWriteBufferSize(BUFFER_SIZE);

        mSerialManager.setListener(new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                // Sending Serial data to TCP
                mTcpManager.writeAsync(data);
            }
            @Override
            public void onRunError(Exception e) {
            }
        });

        mTcpManager.setListener(new TcpInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                // Sending TCP data to Serial
                mSerialManager.writeAsync(data);
            }
            @Override
            public void onRunError(Exception e) {
            }
        });

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.submit(mSerialManager);
        executorService.submit(mTcpManager);
    }

    private void initializeServerAndDrone(@NonNull String systemAddress) {
        mMavsdkServer = new MavsdkServer();
        int mavsdkServerPort = mMavsdkServer.run(systemAddress);

        mDrone = new System(MAVSDK_SERVER_IP, mavsdkServerPort);
    }


    public LiveData<PositionRelative> getPositionRelative() {
        if (mPositionRelativeLiveData == null) {
            Flowable<PositionRelative> positionRelativeFlowable;

            if (GENERATE_DUMMY_DATA) {
                Random random = new Random();
                positionRelativeFlowable = Flowable.interval(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(aLong -> new PositionRelative(100 * random.nextFloat(), 100 * random.nextFloat()));

            } else {
                positionRelativeFlowable = mDrone.getTelemetry().getPositionVelocityNed()
                        .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(positionVelocityNed -> {
                            float distance = (float) Math.hypot(positionVelocityNed.getPosition().getNorthM(), positionVelocityNed.getPosition().getEastM());
                            float height = Math.abs(positionVelocityNed.getPosition().getDownM());
                            return new PositionRelative(distance, height);
                        })
                        .subscribeOn(Schedulers.io());
            }

            mPositionRelativeLiveData = LiveDataReactiveStreams.fromPublisher(positionRelativeFlowable);
        }

        return mPositionRelativeLiveData;
    }

    public LiveData<Speed> getSpeed() {
        if (mSpeedLiveData == null) {
            Flowable<Speed> speedFlowable;

            if (GENERATE_DUMMY_DATA) {
                Random random = new Random();
                speedFlowable = Flowable.interval(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(aLong -> new Speed(10 * random.nextFloat(), 10 * random.nextFloat()));
            } else {
                speedFlowable = mDrone.getTelemetry().getPositionVelocityNed()
                        .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(positionVelocityNed -> {
                            float hspeed = (float) Math.hypot(positionVelocityNed.getVelocity().getNorthMS(), positionVelocityNed.getVelocity().getEastMS());
                            float vspeed = Math.abs(positionVelocityNed.getVelocity().getDownMS());
                            return new Speed(hspeed, vspeed);
                        })
                        .subscribeOn(Schedulers.io());
            }

            mSpeedLiveData = LiveDataReactiveStreams.fromPublisher(speedFlowable);
        }

        return mSpeedLiveData;
    }

    public LiveData<Telemetry.Battery> getBattery() {
        if (mBatteryLiveData == null) {
            Flowable<Telemetry.Battery> batteryFlowable;

            if (GENERATE_DUMMY_DATA) {
                batteryFlowable = Flowable.intervalRange(0, 21, 0, THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(aLong -> new Telemetry.Battery((float) (16.8 + aLong * (12.6 - 16.8) / 21), 1 - aLong.floatValue() * 5 * 0.01f));
            } else {
                batteryFlowable = mDrone.getTelemetry().getBattery()
                        .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
            }

            mBatteryLiveData = LiveDataReactiveStreams.fromPublisher(batteryFlowable);
        }

        return mBatteryLiveData;
    }

    public LiveData<Telemetry.GpsInfo> getGpsInfo() {
        if (mGpsInfoLiveData == null) {
            Flowable<Telemetry.GpsInfo> gpsInfoFlowable;

            if (GENERATE_DUMMY_DATA) {
                gpsInfoFlowable = Flowable.intervalRange(0, 11, 0, THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(aLong -> new Telemetry.GpsInfo((int) (2 * aLong), Telemetry.FixType.FIX_3D));
            } else {
                gpsInfoFlowable = mDrone.getTelemetry().getGpsInfo()
                        .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
            }

            mGpsInfoLiveData = LiveDataReactiveStreams.fromPublisher(gpsInfoFlowable);
        }
        return mGpsInfoLiveData;
    }

    public LiveData<Telemetry.Position> getPosition() {
        if (mPositionLiveData == null) {
            Flowable<Telemetry.Position> positionFlowable;

            if (GENERATE_DUMMY_DATA) {
                List<Integer> arr = IntStream.rangeClosed(0, 50).boxed().collect(Collectors.toList());
                arr.addAll(Lists.reverse(arr));
                positionFlowable = Flowable.intervalRange(0, arr.size(), 0, 100, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(aLong -> new Telemetry.Position(
                                12.904993 + (12.906055 - 12.904993) * arr.get(aLong.intValue()) / (arr.size()/2),
                                80.157708 + (80.159610 - 80.157708) * arr.get(aLong.intValue()) / (arr.size()/2),
                                80f,
                                20f
                        ));
            } else {
                positionFlowable = mDrone.getTelemetry().getPosition()
                        .throttleLast(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
            }

            mPositionLiveData = LiveDataReactiveStreams.fromPublisher(positionFlowable);
        }

        return mPositionLiveData;
    }


    public void destroy() {
        mCompositeDisposable.dispose();
        mDrone.dispose();
        mMavsdkServer.stop();

        if (mSerialManager != null) {
            mSerialManager.stop();
        }

        if (mTcpManager != null) {
            mTcpManager.stop();
        }
    }
}
