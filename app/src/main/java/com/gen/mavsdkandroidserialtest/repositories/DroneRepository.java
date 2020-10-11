package com.gen.mavsdkandroidserialtest.repositories;

import android.app.Application;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;

import com.gen.mavsdkandroidserialtest.R;
import com.gen.mavsdkandroidserialtest.models.PositionRelative;
import com.gen.mavsdkandroidserialtest.models.Speed;
import com.google.common.collect.Lists;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.mavsdk.System;
import io.mavsdk.mavsdkserver.MavsdkServer;
import io.mavsdk.telemetry.Telemetry;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class DroneRepository {
    private static final String TAG = "LOG_" + DroneRepository.class.getName();
    private static final boolean GENERATE_DUMMY_DATA = false;
    private static final boolean IS_SIMULATION = false;

    private static final String NO_ADDRESS = "no_address";
    private static final int USB_BAUD_RATE = 57600;
    private static final String MAVSDK_SERVER_IP = "127.0.0.1";
    private static final long THROTTLE_TIME_MILLIS = 500;

    private static DroneRepository instance;

    private System mDrone;
    private MavsdkServer mMavsdkServer;
    private Context mAppContext;
    private UsbDeviceConnection connection;

    private LiveData<PositionRelative> mPositionRelativeLiveData;
    private LiveData<Speed> mSpeedLiveData;
    private LiveData<Telemetry.Battery> mBatteryLiveData;
    private LiveData<Telemetry.GpsInfo> mGpsInfoLiveData;
    private LiveData<Telemetry.Position> mPositionLiveData;

    public static DroneRepository getInstance(Application application) {
        if (instance == null) {
            instance = new DroneRepository(application);
        }
        return instance;
    }

    private DroneRepository(Application application) {
        mAppContext = application.getApplicationContext();

        if (GENERATE_DUMMY_DATA) {
            initializeDummyDataStreams();
        } else {
            if (IS_SIMULATION) {
                initializeServerAndDrone("udp://192.168.0.255:14550");
            } else {
                initializeServerAndDrone(initializeUsbDevice());
            }
            initializeDataStreams();
        }
    }

    // Using usb-serial-for-android
    private String initializeUsbDevice() {
        UsbManager usbManager = (UsbManager) mAppContext.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> driverList = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (driverList.isEmpty()) {
            Toast.makeText(mAppContext, R.string.str_usb_device_not_found, Toast.LENGTH_SHORT).show();
            return NO_ADDRESS;
        }

        UsbSerialDriver usbSerialDriver = driverList.get(0);
        UsbDevice usbDevice = usbSerialDriver.getDevice();
        boolean hasPermission = usbManager.hasPermission(usbDevice);

        if (!hasPermission) {
            Toast.makeText(mAppContext, R.string.str_usb_permission_not_granted, Toast.LENGTH_SHORT).show();
            return NO_ADDRESS;
        }

        connection = usbManager.openDevice(usbDevice);
        UsbSerialPort usbSerialPort = usbSerialDriver.getPorts().get(0);

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
        Log.d(TAG, "initializeUsbDevice: port isOpen: " + usbSerialPort.isOpen());


        // Remove comments to read the device data on Java side
//        SerialInputOutputManager inputOutputManager = new SerialInputOutputManager(usbSerialPort);
//
//        inputOutputManager.setReadTimeout(1000);
//        inputOutputManager.setReadBufferSize(2048);
//        inputOutputManager.setWriteTimeout(1000);
//        inputOutputManager.setWriteBufferSize(20488);
//        inputOutputManager.setListener(new SerialInputOutputManager.Listener() {
//            @Override
//            public void onNewData(byte[] data) {
//                Log.d(TAG, "onNewData: " + Arrays.toString(data));
//                Log.d(TAG, "onNewData: fd: " + connection.getFileDescriptor());
//            }
//            @Override
//            public void onRunError(Exception e) {
//                Log.d(TAG, "onRunError: " + e);
//            }
//        });
//        Executors.newSingleThreadExecutor().submit(inputOutputManager);


        String systemAddress = "serial_fd://" + connection.getFileDescriptor() + ":" + USB_BAUD_RATE;
//        String droneSystemAddress = "serial://" + usbDevice.getDeviceName() + ":" + USB_BAUD_RATE;

        Log.d(TAG, "initializeUsbDevice: " + systemAddress);
        return systemAddress;
    }


    // Without using usb-serial-for-android
//    private String initializeUsbDevice() {
//        UsbManager usbManager = (UsbManager) mAppContext.getSystemService(Context.USB_SERVICE);
//        HashMap<String, UsbDevice> deviceHm = usbManager.getDeviceList();
//        if (deviceHm.isEmpty()) {
//            return NO_ADDRESS;
//        }
//
//        UsbDevice usbDevice = deviceHm.values().iterator().next();
//        boolean hasPermission = usbManager.hasPermission(usbDevice);
//        if (!hasPermission) {
//            Toast.makeText(mAppContext, R.string.str_usb_permission_not_granted, Toast.LENGTH_SHORT).show();
//            return NO_ADDRESS;
//        }
//
//        UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
//
//        String systemAddress = "serial_fd://" + connection.getFileDescriptor() + ":" + USB_BAUD_RATE;
////        String systemAddress = "serial://" + usbDevice.getDeviceName() + ":" + USB_BAUD_RATE;
//
//        Log.d(TAG, "initializeUsbDevice: " + systemAddress);
//        return systemAddress;
//    }


    private void initializeServerAndDrone(String systemAddress) {
        mMavsdkServer = new MavsdkServer();
        int mavsdkServerPort = mMavsdkServer.run(systemAddress);
        mDrone = new System(MAVSDK_SERVER_IP, mavsdkServerPort);

//        connection.close();
    }

    private void initializeDataStreams() {
        // Position
        Flowable<PositionRelative> positionRelativeFlowable =
                mDrone.getTelemetry().getPositionVelocityNed()
                        .throttleFirst(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Function<Telemetry.PositionVelocityNed, PositionRelative>() {
                            @Override
                            public PositionRelative apply(Telemetry.PositionVelocityNed positionVelocityNed) throws Exception {
                                float distance = (float) Math.hypot(positionVelocityNed.getPosition().getNorthM(), positionVelocityNed.getPosition().getEastM());
                                float height = Math.abs(positionVelocityNed.getPosition().getDownM());
                                return new PositionRelative(distance, height);
                            }
                        })
                        .subscribeOn(Schedulers.io());
        mPositionRelativeLiveData = LiveDataReactiveStreams.fromPublisher(positionRelativeFlowable);

        // Speed
        Flowable<Speed> speedFlowable =
                mDrone.getTelemetry().getPositionVelocityNed()
                        .throttleFirst(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Function<Telemetry.PositionVelocityNed, Speed>() {
                            @Override
                            public Speed apply(Telemetry.PositionVelocityNed positionVelocityNed) throws Exception {
                                float hspeed = (float) Math.hypot(positionVelocityNed.getVelocity().getNorthMS(), positionVelocityNed.getVelocity().getEastMS());
                                float vspeed = Math.abs(positionVelocityNed.getVelocity().getDownMS());
                                return new Speed(hspeed, vspeed);
                            }
                        })
                        .subscribeOn(Schedulers.io());
        mSpeedLiveData =  LiveDataReactiveStreams.fromPublisher(speedFlowable);

        // Battery
        Flowable<Telemetry.Battery> batteryFlowable =
                mDrone.getTelemetry().getBattery()
                        .throttleFirst(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
        mBatteryLiveData = LiveDataReactiveStreams.fromPublisher(batteryFlowable);

        // GpsInfo
        Flowable<Telemetry.GpsInfo> gpsInfoFlowable =
                mDrone.getTelemetry().getGpsInfo()
                        .throttleFirst(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
        mGpsInfoLiveData = LiveDataReactiveStreams.fromPublisher(gpsInfoFlowable);

        // Location
        Flowable<Telemetry.Position> positionFlowable =
                mDrone.getTelemetry().getPosition()
                        .throttleFirst(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io());
        mPositionLiveData = LiveDataReactiveStreams.fromPublisher(positionFlowable);
    }

    private void initializeDummyDataStreams() {
        Random random = new Random();

        // Position
        Flowable<PositionRelative> positionRelativeDummyFlowable =
                Flowable
                        .interval(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, PositionRelative>() {
                            @Override
                            public PositionRelative apply(Long aLong) throws Exception {
                                return new PositionRelative(100 * random.nextFloat(), 100 * random.nextFloat());
                            }
                        });
        mPositionRelativeLiveData = LiveDataReactiveStreams.fromPublisher(positionRelativeDummyFlowable);

        // Speed
        Flowable<Speed> droneSpeedDummyFlowable =
                Flowable
                        .interval(THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Function<Long, Speed>() {
                            @Override
                            public Speed apply(Long aLong) throws Exception {
                                return new Speed(10 * random.nextFloat(), 10 * random.nextFloat());
                            }
                        });
        mSpeedLiveData = LiveDataReactiveStreams.fromPublisher(droneSpeedDummyFlowable);

        // Battery
        Flowable<Telemetry.Battery> batteryDummyFlowable =
                Flowable
                        .intervalRange(0, 21, 0, THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(new Function<Long, Telemetry.Battery>() {
                            @Override
                            public Telemetry.Battery apply(Long aLong) throws Exception {
                                return new Telemetry.Battery((float) (16.8 + aLong * (12.6 - 16.8) / 21), 1 - aLong.floatValue() * 5 * 0.01f);
                            }
                        });
        mBatteryLiveData = LiveDataReactiveStreams.fromPublisher(batteryDummyFlowable);

        // GpsInfo
        Flowable<Telemetry.GpsInfo> gpsInfoDummyFlowable =
                Flowable
                        .intervalRange(0, 11, 0, THROTTLE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(new Function<Long, Telemetry.GpsInfo>() {
                            @Override
                            public Telemetry.GpsInfo apply(Long aLong) throws Exception {
                                return new Telemetry.GpsInfo((int) (2 * aLong), Telemetry.FixType.FIX_3D);
                            }
                        });
        mGpsInfoLiveData = LiveDataReactiveStreams.fromPublisher(gpsInfoDummyFlowable);

        // Position
        List<Integer> arr = IntStream.rangeClosed(0, 50).boxed().collect(Collectors.toList());
        arr.addAll(Lists.reverse(arr));
        Flowable<Telemetry.Position> positionDummyFlowable =
                Flowable
                        .intervalRange(0, arr.size(), 0, 100, TimeUnit.MILLISECONDS)
                        .repeat()
                        .map(new Function<Long, Telemetry.Position>() {
                            @Override
                            public Telemetry.Position apply(Long aLong) throws Exception {
                                return new Telemetry.Position(
                                        12.904993 + (12.906055 - 12.904993) * arr.get(aLong.intValue()) / (arr.size()/2),
                                        80.157708 + (80.159610 - 80.157708) * arr.get(aLong.intValue()) / (arr.size()/2),
                                        80f,
                                        20f
                                );
                            }
                        });

        mPositionLiveData = LiveDataReactiveStreams.fromPublisher(positionDummyFlowable);
    }


    public LiveData<PositionRelative> getPositionRelative() {
        return mPositionRelativeLiveData;
    }

    public LiveData<Speed> getSpeed() {
        return mSpeedLiveData;
    }

    public LiveData<Telemetry.Battery> getBattery() {
        return mBatteryLiveData;
    }

    public LiveData<Telemetry.GpsInfo> getGpsInfo() {
        return mGpsInfoLiveData;
    }

    public LiveData<Telemetry.Position> getPosition() {
        return mPositionLiveData;
    }

    public void destroy() {
        mDrone.dispose();
        mMavsdkServer.stop();
    }
}
