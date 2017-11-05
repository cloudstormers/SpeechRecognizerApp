package telocate.de.speechrecognition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;


import telocate.de.speechrecognition.threads.InsecureListeningThread;
import telocate.de.speechrecognition.threads.SendProcessThread;


public class MainActivity extends AppCompatActivity {

    // TODO: think about name generation.
    private static final String CLIENT_BLUETOOTH_NAME = "PersonWithoutDisability";

    private final int REQ_CODE_SPEECH_INPUT = 100;

    // Constants for periods of discoverability.
    private static final int INFINITE = 0;

    private InsecureListeningThread mInsecureListeningThread;
    private SendProcessThread mSendProcessThread;

    private BluetoothAdapter mBluetoothModule = null;
    Context context = this;


    String mConnectedReceiver;

    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mBluetoothSocket;

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_SERVER_SOCKET_RECEIVED:
                    mServerSocket = (BluetoothServerSocket) msg.obj;
                    break;
                case Constants.CONNECTION_MADE:
                    // save the connected device's name
                    mConnectedReceiver = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(context, "Connected to: " + mConnectedReceiver, Toast.LENGTH_SHORT).show();
                    mBluetoothSocket = (BluetoothSocket) msg.obj;
                    mInsecureListeningThread = null;
                    mSendProcessThread = new SendProcessThread(context, mBluetoothSocket, mHandler);
                    mSendProcessThread.start();
                    break;
                case Constants.LISTENING_FAILED:
                    mInsecureListeningThread = null;
                    break;
                case Constants.CONNECTION_LOST:
                    Toast.makeText(context, msg.getData().getString(Constants.TOAST), Toast.LENGTH_LONG).show();
                    nullReceiveSendThreads();
                    startInsecureListeningThread();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(Constants.TOAST), Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };


    private TextView txtSpeechInput;
    private ImageButton btnSpeak;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);



        txtSpeechInput = (TextView) findViewById(R.id.txtSpeechInput);
        btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        // Get local Bluetooth adapter.
        mBluetoothModule = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothModule == null) {
            // If the adapter is null, then Bluetooth is not supported.
            Toast.makeText(context, getString(R.string.bt_module_not_available_message), Toast.LENGTH_LONG).show();
            finish();
        } else {
            // Else device has bluetooth module. Turn on discoverability.
            if (mBluetoothModule.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                setDiscoverable(INFINITE);
            } else {
                // Else it is already discoverable.
                mBluetoothModule.setName(CLIENT_BLUETOOTH_NAME);
                startListeningForConnection();
            }
        }
    }


    public void write(byte[] out) {
        // Perform the write unsynchronized
        if (mSendProcessThread != null)
            mSendProcessThread.write(out);
        else
            Toast.makeText(this, "send process thread null", Toast.LENGTH_SHORT).show();
    }

    /**
     * Showing google speech input dialog
     * */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "us-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    txtSpeechInput.setText(result.get(0));
                    byte[] send = result.get(0).getBytes();
                    write(send);
                }
                break;
            }

        }
    }


    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        Toast.makeText(context, getString(R.string.bt_turned_on_message), Toast.LENGTH_SHORT).show();
                        mBluetoothModule.setName(CLIENT_BLUETOOTH_NAME);
                        startListeningForConnection();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Toast.makeText(context, "Bluetooth turning off.", Toast.LENGTH_SHORT).show();
                    default:
                        break;
                }
            }
        }
    };

    public void startListeningForConnection() {
        /* If it's not running run. */
        startInsecureListeningThread();
    }

    /** Called upon clicking STOP button. */
    public void stopListeningFoConnection() {
        /* If it's running stop synchronization. */
        stopAllWorkerThreads();

    }

    private void nullReceiveSendThreads() {
        mSendProcessThread = null;
    }

    private void stopAllWorkerThreads() {

        if (mInsecureListeningThread != null) {
            try {
                mServerSocket.close();
                mInsecureListeningThread.join();
                mInsecureListeningThread = null;
                Toast.makeText(context, "mInsecureListeningThread Closed successfully", Toast.LENGTH_SHORT).show();
            } catch (InterruptedException e) {
                Toast.makeText(context, "mInsecureListeningThread InterruptedException", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(context, "mInsecureListeningThread IO Exception", Toast.LENGTH_SHORT).show();
            }
        }

        if (mSendProcessThread != null) {
            try {
                mSendProcessThread.interrupt();
                mSendProcessThread.join();
                mSendProcessThread = null;
                //Toast.makeText(context, "mSendProcessThread closed successfully", Toast.LENGTH_SHORT).show();
            } catch (InterruptedException e) {
                Toast.makeText(context, "mSendProcessThread InterruptedException", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startInsecureListeningThread() {
        if (mInsecureListeningThread == null) {
            mInsecureListeningThread = new InsecureListeningThread(mHandler);
            mInsecureListeningThread.start();
        }
    }

    /* Register for broadcasts, when BluetoothAdapter state changes. */
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mStateReceiver, filter);
    }

    /* Unregister broadcast receiver. */
    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mStateReceiver);
    }

    /**
     * Makes this device discoverable for discoverableTimeInSec seconds.
     */
    private void setDiscoverable(int discoverableTimeInSec) {
        if (mBluetoothModule.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableTimeInSec);
            startActivity(discoverableIntent);
        }
    }


} // End of MainActivity.