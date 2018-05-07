package com.test.Crazepony;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Created by EASON GUO on 2018/3/28.
 * Email guoxionglong@makerfire.com
 */

public class BluetoothLeService extends Service
{
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    public static BluetoothAdapter mBluetoothAdapter;
    public static String mBluetoothDeviceAddress;
    public static BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            TAG+".ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            TAG+".ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            TAG+".ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            TAG+".ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            TAG+".EXTRA_DATA";


    //0xffe0
    //0000ffe1-0000-1000-8000-00805f9b34fb
    //0000ffe4-0000-1000-8000-00805f9b34fb

    //旧的UUID
    public final static UUID OLD_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");


    //用于数据接收、发送的service和character对应的UUID，由改ble透传模块决定
    public final static UUID UUID_NOTIFY =
            UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb");


    public final static UUID UUID_SERVICE =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    //用于发送的UUID
    public final static UUID UUID_WRITE =
            UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb");


    public final static UUID UUID_LED_SET_BIT7 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");

    public final static UUID UUID_LED_BIT7 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");


    //用于数据发送的character，由改ble透传模块决定
    public static BluetoothGattCharacteristic mSendLedSetBit7Characteristic = null;

    public static BluetoothGattCharacteristic mSendLedBit7Characteristic = null;


    public static BluetoothGattCharacteristic mSendCharacteristic = null;

    //用于数据接收、发送的character，由改ble透传  模块决定
    public static BluetoothGattCharacteristic mNotifyCharacteristic;


    private Activity activity;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {


            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                mSendCharacteristic = null;

                //绑定BLE收发服务mServiceConnection
//                initialize();
            }
        }


        //再次连接蓝牙的时候,需要重新复制写Characteristic值和读的,并且要在onServicesDiscovered发送被调用之后才能去赋值.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {

            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {

            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
//            BTClient.processConnect();
        }
    };

    private void broadcastUpdate(final String action)
    {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic)
    {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();

        intent.putExtra(EXTRA_DATA, data);
        sendBroadcast(intent);

    }

    public class LocalBinder extends Binder
    {
        public BluetoothLeService getService()
        {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize()
    {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }


    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address)
    {
        if (mBluetoothAdapter == null || address == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null)
        {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect())
            {
                mConnectionState = STATE_CONNECTING;

                return true;
            } else
            {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null)
        {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect()
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close()
    {
        if (mBluetoothGatt == null)
        {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }


    public void setActivity(Activity activity)
    {
        this.activity = activity;
    }


    //将字符串写入ble发送出去
    public void writeCharacteristic(byte[] data)
    {
        if (mBluetoothGatt != null)
        {
            if (mBluetoothGatt != null && mBluetoothGatt.getService(UUID_SERVICE) != null)
            {
                BluetoothGattCharacteristic bluetoothGattCharacteristic = mBluetoothGatt.getService(UUID_SERVICE).getCharacteristic(UUID_WRITE);

                if (bluetoothGattCharacteristic != null)
                {
                    mSendCharacteristic = bluetoothGattCharacteristic;
                }
            }

            if (mSendCharacteristic != null && mBluetoothGatt != null)
            {
                mSendCharacteristic.setValue(data);
                boolean init = false;
                try  //36776001616
                {
                    init = mBluetoothGatt.writeCharacteristic(mSendCharacteristic);
                } catch (Exception e)
                {
                    e.printStackTrace();
                }

                if (init == true)
                {
                    System.out.print("写出数据:");
                    for (int i = 0; i < data.length; i++)
                    {
                        System.out.print(data[i]);
                    }
                    System.out.println("");
                } else if (init == false)
                {

                }
            }
        }
    }


    public static void writeCharacteristic(BluetoothGattCharacteristic bluetoothGattCharacteristic, byte[] data)
    {
        bluetoothGattCharacteristic.setValue(data);
//        if (mBluetoothGatt != null)
//        {
//            mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
//        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices()
    {
        if (mBluetoothGatt == null) return null;

        String uuidServer = null;

        String uuidChara = null;

        //将所有的GATT服务都打印出来
        List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices)
        {
            uuidServer = gattService.getUuid().toString();

            Log.i(TAG, "gattService:" + uuidServer);
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
            {
                uuidChara = gattCharacteristic.getUuid().toString();
                Log.i(TAG, "gattCharacteristic:" + uuidChara);

                if (uuidChara.equalsIgnoreCase(OLD_UUID.toString()))
                {
                    mNotifyCharacteristic = gattCharacteristic;
                    mSendCharacteristic = mNotifyCharacteristic;
                    mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);
                } else
                {
                    if (uuidChara.equalsIgnoreCase(UUID_NOTIFY.toString()))
                    {

                        mNotifyCharacteristic = gattCharacteristic;
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);
                        //需要为指定特征的特定的描述符设置启用才行
                        BluetoothGattDescriptor descriptor = mNotifyCharacteristic.getDescriptor(UUID
                                .fromString(UUID_NOTIFY.toString()));
                        if (descriptor != null)
                        {
                            System.out.println("write descriptor");
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor);
                        }
                    } else
                    {

                    }
                    if (uuidChara.equalsIgnoreCase(UUID_WRITE.toString()))
                    {
                        mSendCharacteristic = gattCharacteristic;

                    } else
                    {
                    }
                    if (uuidChara.equalsIgnoreCase(UUID_LED_SET_BIT7.toString()))
                    {
                        mSendLedSetBit7Characteristic = gattCharacteristic;

                        MyThread myThread = new MyThread();
                        myThread.start();
//                        byte[] bytes = new byte[1];
//                        bytes[0] = (byte) 255;
//                        LogUtil.LOGI("写出LED数据...");
//                        BluetoothLeService.writeCharacteristic(mSendLedSetBit7Characteristic, bytes);
                    } else
                    {

                    }

                    if (uuidChara.equalsIgnoreCase(UUID_LED_BIT7.toString()))
                    {
                        mSendLedBit7Characteristic = gattCharacteristic;
                        MyThread2 myThread2 = new MyThread2();
                        myThread2.start();
                    } else
                    {

                    }
                }
            }
        }
        return gattServices;
    }

    private class MyThread extends Thread
    {
        @Override
        public void run()
        {
            while (true)
            {
                byte[] bytes = new byte[1];
                bytes[0] = (byte) 255;
//                LogUtil.LOGI("写出LED数据...");
                BluetoothLeService.writeCharacteristic(mSendLedSetBit7Characteristic, bytes);
                try
                {
                    Thread.sleep(1000);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

        }
    }

    ;

    private class MyThread2 extends Thread
    {
        private boolean bool = true;

        @Override
        public void run()
        {
            while (true)
            {
                byte[] bytes = new byte[1];
                if (bool)
                {
                    bytes[0] = (byte) 255;
                    bool = false;
                } else
                {
                    bytes[0] = (byte) 0;
                    bool = true;
                }
//                LogUtil.LOGI("写出LED数据...");
                BluetoothLeService.writeCharacteristic(mSendLedBit7Characteristic, bytes);
                try
                {
                    Thread.sleep(250);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

        }
    }

    public static void closeLED()
    {
        if (mSendLedBit7Characteristic != null)
        {
            byte[] bytes = new byte[1];
            bytes[0] = (byte) 0;
//            LogUtil.LOGI("写出LED数据...");
            BluetoothLeService.writeCharacteristic(mSendLedBit7Characteristic, bytes);
        }
    }

}

