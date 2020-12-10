package com.gen.mavsdkandroidserialtest.io;

import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TcpInputOutputManager implements Runnable {

    private static final String TAG = "LOG_" + TcpInputOutputManager.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int BUFFER_SIZE = 4096;

    private final Object mReadBufferLock = new Object();
    private final Object mWriteBufferLock = new Object();

    private ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    public enum State {
        STOPPED,
        RUNNING,
        STOPPING;
    }

    private final int mServerPort;
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private Listener mListener;
    private DataInputStream mInputStream;
    private DataOutputStream mOutputStream;

    private State mState = State.STOPPED; // Synchronized by 'this'
    private int mThreadPriority = Process.THREAD_PRIORITY_URGENT_AUDIO;

    public interface Listener {
        void onNewData(byte[] data);
        void onRunError(Exception e);
    }

    public TcpInputOutputManager(int serverPort) {
        mServerPort = serverPort;
    }

    public TcpInputOutputManager(int serverPort, @Nullable Listener listener) {
        mServerPort = serverPort;
        mListener = listener;
    }

    public synchronized void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @Nullable
    public synchronized Listener getListener() {
        return mListener;
    }

    public void setThreadPriority(int threadPriority) {
        if (mState != State.STOPPED)
            throw new IllegalStateException("threadPriority only configurable before TcpInputOutputManager is started");
        mThreadPriority = threadPriority;
    }

    public void setReadBufferSize(int bufferSize) {
        if (getReadBufferSize() == bufferSize)
            return;
        synchronized (mReadBufferLock) {
            mReadBuffer = ByteBuffer.allocate(bufferSize);
        }
    }

    public int getReadBufferSize() {
        return mReadBuffer.capacity();
    }

    public void setWriteBufferSize(int bufferSize) {
        if(getWriteBufferSize() == bufferSize)
            return;
        synchronized (mWriteBufferLock) {
            ByteBuffer newWriteBuffer = ByteBuffer.allocate(bufferSize);
            if(mWriteBuffer.position() > 0)
                newWriteBuffer.put(mWriteBuffer.array(), 0, mWriteBuffer.position());
            mWriteBuffer = newWriteBuffer;
        }
    }

    public int getWriteBufferSize() {
        return mWriteBuffer.capacity();
    }

    public void writeAsync(byte[] data) {
        synchronized (mWriteBufferLock) {
            mWriteBuffer.put(data);
        }
    }

    public synchronized void stop() {
        if (getState() == State.RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = State.STOPPING;
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized State getState() {
        return mState;
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mServerPort);
            mServerSocket.setReceiveBufferSize(BUFFER_SIZE);

            mSocket = mServerSocket.accept();
            mSocket.setReceiveBufferSize(getReadBufferSize());
            mSocket.setSendBufferSize(getWriteBufferSize());

            mInputStream = new DataInputStream(mSocket.getInputStream());
            mOutputStream = new DataOutputStream(mSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mThreadPriority != Process.THREAD_PRIORITY_DEFAULT)
            setThreadPriority(mThreadPriority);

        synchronized (this) {
            if (getState() != State.STOPPED) {
                throw new IllegalStateException("Already running");
            }
            mState = State.RUNNING;
        }

        Log.i(TAG, "Running ...");
        try {
            while (true) {
                if (getState() != State.RUNNING) {
                    Log.i(TAG, "Stopping mState=" + getState());
                    break;
                }
                step();
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final Listener listener = getListener();
            if (listener != null) {
                listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = State.STOPPED;
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "Stopped");
            }
        }
    }

    private void step() throws IOException {
        // Handle outgoing data.
        byte[] buffer = null;
        int len;
        synchronized (mWriteBufferLock) {
            len = mWriteBuffer.position();
            if (len > 0) {
                buffer = new byte[len];
                mWriteBuffer.rewind();
                mWriteBuffer.get(buffer, 0, len);
                mWriteBuffer.clear();
            }
        }
        if (buffer != null) {
            if (DEBUG) {
                Log.d(TAG, "Writing data len=" + len);
            }
            mOutputStream.write(buffer);
        }

        // Handle incoming data.
        buffer = null;
        synchronized (mReadBufferLock) {
            buffer = mReadBuffer.array();
        }
        if (mInputStream.available() == 0) {
            return;
        }
        len = mInputStream.read(buffer);
        if (len > 0) {
            if (DEBUG) Log.d(TAG, "Read data len=" + len);
            final Listener listener = getListener();
            if (listener != null) {
                final byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                listener.onNewData(data);
            }
        }
    }
}