package telocate.de.speechrecognition.threads;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import telocate.de.speechrecognition.Constants;


/**
 * Created by aleks on 8/7/2017.
 */

public class SendProcessThread extends Thread {

    private static final int THREAD_SLEEPING_TIME_MSEC = 10;


    boolean mRunning;


    /**************************************************************************/

    private static final String TAG = "SendProcessThread";
    private static final String SEND_PROCESS_THREAD_NAME = "SendProcessThread";

    // Thread inner state.
    private int mState;
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device

    private final BluetoothSocket mBluetoothSocket;
    private final OutputStream mOutStream;
    private Context mContext;

    Handler mHandler;

    public SendProcessThread(Context context, BluetoothSocket bluetoothSocket, Handler handler) {
        mContext = context;
        mHandler = handler;
        mBluetoothSocket = bluetoothSocket;
        OutputStream tmpOut = null;

        // Get the BluetoothSocket output stream.
        try {
            tmpOut = bluetoothSocket.getOutputStream();
        } catch (IOException e) {
            sendToastMessage("SendProcessThread: This should never happen!");
        }

        mOutStream = tmpOut;
        mState = STATE_CONNECTED;

        initialize();
    }

    public void run() {

        setName(SEND_PROCESS_THREAD_NAME);

        while (mState == STATE_CONNECTED) {
            try {
                // CPU power saving.
                sleepMs(THREAD_SLEEPING_TIME_MSEC);


            } catch (InterruptedException e) {
                cleanUpAndStop();
                sendToastMessage("SendProcessThread: failed due to interrupt.");
            }
        }
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            mOutStream.write(buffer);

        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void cleanUpAndStop() {
        try {
            mState = STATE_NONE;
            mBluetoothSocket.close();
        } catch (IOException e) {
            sendToastMessage("close() of connect socket failed");
        }
    }





    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    public void writingErrorOccurred() {

        // This stops the thread and cleans everything up.
        cleanUpAndStop();
        // Send message that connection error occurred.
        Message msg = mHandler.obtainMessage(Constants.CONNECTION_LOST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "SendProcessThread: failed due to IO exception.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void initialize() {
        mRunning = false;
    }


    /* Used for pausing the thread for some time. */
    private void sleepMs(int time) throws InterruptedException{
        Thread.sleep(time);
    }

    private void sendToastMessage(String toast) {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, toast);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

}
