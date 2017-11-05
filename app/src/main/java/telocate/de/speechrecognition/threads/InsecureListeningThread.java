package telocate.de.speechrecognition.threads;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.util.UUID;

import telocate.de.speechrecognition.Constants;


/**
 * Created by aleks on 8/7/2017.
 */

public class InsecureListeningThread extends Thread {

    private static final String TAG = "InsecureListeningThread";
    private static final String LISTENING_THREAD_NAME = "InsecureAcceptingThread";

    // Name for the SDP record when creating BluetoothServerSocket.
    private static final String NAME_INSECURE = "CloudStormersBluetoothService";

    // Common UUID for this client app and also server app.
    private static final UUID MY_UUID_INSECURE = UUID.fromString("0c3d9152-2d18-47ed-abed-f207ec3e31b7");

    // Bluetooth module
    private BluetoothAdapter mBluetoothModule = null;

    // The local server socket
    private final BluetoothServerSocket mmServerSocket;

    private Handler mHandler;

    boolean failed = false;


    public InsecureListeningThread(Handler handler) {
        mHandler = handler;
        mBluetoothModule = BluetoothAdapter.getDefaultAdapter();
        BluetoothServerSocket tmp = null;
        sendToastMessage("Created InsecureListeningThread");

        // Create a new listening server socket
        try {
            tmp = mBluetoothModule.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
        } catch (IOException e) {
            // Happens when the thread is started with bluetooth off.
            failed = true;
        }

        mmServerSocket = tmp;
        // Send server socket to main activity so it can be closed.
        notifyObtainedServerSocket();
    }

    public void run() {

        if (!failed) {

            setName(LISTENING_THREAD_NAME);

            BluetoothSocket bluetoothSocket = null;

            try {
                sendToastMessage("Currently blocked on accept() method.");
                // This is a blocking call and will only return on a
                // successful connection or an exception.
                bluetoothSocket = mmServerSocket.accept();
            } catch (IOException e) {
                sendToastMessage("InsecureListeningThread: accept() failed.");
                failed = true;
                closeServerSocket();
            }

            // If a connection was accepted
            if (bluetoothSocket != null) {

                final String connectedDeviceName = bluetoothSocket.getRemoteDevice().getName();
                // SEND BLUETOOTHSOCKET OBJECT TO MAIN ACTIVITY TO SPAWN RECEIVING AND SENDING THREAD.
                notifyMadeConnection(connectedDeviceName, bluetoothSocket);
                closeServerSocket();
            }
        } else {
            sendToastMessage("Unable to run InsecureListeningThread, turn on the bluetooth and try again.");
        }

        if (failed) {
            notifyFailed();
        }

    }

    public void closeServerSocket() {
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendToastMessage(String toast) {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, toast);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void notifyObtainedServerSocket() {
        Message msg = Message.obtain();
        msg.what = Constants.MESSAGE_SERVER_SOCKET_RECEIVED;
        msg.obj = mmServerSocket;
        mHandler.sendMessage(msg);
    }

    private void notifyMadeConnection(String connectedDeviceName, BluetoothSocket bluetoothSocket) {
        Message msg = Message.obtain();
        msg.what = Constants.CONNECTION_MADE;
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, connectedDeviceName);
        msg.setData(bundle);
        msg.obj = bluetoothSocket;
        mHandler.sendMessage(msg);
    }

    private void notifyFailed() {
        Message msg = mHandler.obtainMessage(Constants.LISTENING_FAILED);
        mHandler.sendMessage(msg);
    }
}
