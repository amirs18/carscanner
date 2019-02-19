package com.example.myapplication;


import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.Tag;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.renderscript.RenderScript;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    public static int var=1;
    private  static String KEY_temp="1";
    Handler handler1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String deviceAddress;
    TextView txt;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ArrayList deviceStrs = new ArrayList();
        txt=findViewById(R.id.txt);
        final ArrayList devices = new ArrayList();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(mReceiver, filter);
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // set up list selection handlers
            for (BluetoothDevice device : pairedDevices) {
                deviceStrs.add(device.getName() + "\n" + device.getAddress());
                devices.add(device.getAddress());
            }

            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

            ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                    deviceStrs.toArray(new String[deviceStrs.size()]));

            alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    String deviceAddress = (String) devices.get(position);
                    // TODO save deviceAddress

                    BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
                    livedata livedata=new livedata(handler1,device);
                    livedata.start();
                }
            });
            alertDialog.setTitle("Choose Bluetooth device");
            alertDialog.show();
            handler1=new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    txt.setText(msg.getData().getString(KEY_temp));
                    return true;
                }
            });

        }
    }
    private static BluetoothSocket connect(BluetoothDevice dev) throws IOException {
        int i=0;
        BluetoothSocket sock = null;
        BluetoothSocket sockFallback;
        BluetoothAdapter bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.cancelDiscovery();

        Log.d("myee", "Starting Bluetooth connection..");

            try {
                sock = dev.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                sock.connect();
            } catch (Exception e1) {
                Log.e("myee", "There was an error while establishing Bluetooth connection. Falling back..", e1);
                if(sock!=null) {
                    Class<?> clazz = sock.getRemoteDevice().getClass();
                    Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
                    try {
                        Log.e("","trying fallback...");

                        sock =(BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(dev,1);
                        sock.connect();

                        Log.e("myee","Connected");
                    }
                    catch (Exception e2) {
                        Log.e("myee", "Couldn't establish Bluetooth connection!");
                    }
                }
            }


        return sock;
    }
    class livedata extends Thread{
        BluetoothSocket socket=null;
        Handler handler;
        BluetoothDevice device;
        public  livedata(Handler handler1,BluetoothDevice device){
            this.handler=handler1;
            this.device=device;
        }

        @Override
        public void run() {
            super.run();
            String tmp;

            try {

                socket=connect(device);
                try {
                    new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new TimeoutCommand(125).run(socket.getInputStream(), socket.getOutputStream());
                    new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());

                } catch (Exception e) {
                    Log.d("myee",e+"");
                    // handle errors
                }
            } catch (IOException e) {
                Log.d("myee", e.getMessage());
            }



            while (true){
                EngineCoolantTemperatureCommand r =new EngineCoolantTemperatureCommand();
                try {
                    r.run(socket.getInputStream(), socket.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                tmp=r.getFormattedResult();
                tmp.replace('C','0');
                if(Integer.parseInt(tmp) > 110){

                }
                Message message=new Message();
                Bundle bundle= new Bundle();
                bundle.putString(KEY_temp,r.getFormattedResult());
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }
    }


}


