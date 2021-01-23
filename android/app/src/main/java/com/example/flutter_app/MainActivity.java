package com.example.flutter_app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

import com.brother.ptouch.sdk.BLEPrinter;
import com.brother.ptouch.sdk.LabelInfo;
import com.brother.ptouch.sdk.Printer;
import com.brother.ptouch.sdk.PrinterInfo;
import com.brother.ptouch.sdk.PrinterStatus;
import com.brother.ptouch.sdk.connection.BluetoothConnectionSetting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends FlutterActivity {

    private static final String CHANNEL = "brother/print";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);


        new MethodChannel(getFlutterView(), CHANNEL).setMethodCallHandler(
                new MethodChannel.MethodCallHandler() {
                    @Override
                    public void onMethodCall(MethodCall call, MethodChannel.Result result) {

                        if (call.method.equals("print")) {

                            String address = call.argument("address");
                            String base64 = call.argument("bas64");

                            // Specify printer
                            final Printer printer = new Printer();
                            PrinterInfo settings = printer.getPrinterInfo();
                            printer.setBluetooth(BluetoothAdapter.getDefaultAdapter());
                            settings.printerModel = PrinterInfo.Model.QL_820NWB;
                            settings.port = PrinterInfo.Port.BLUETOOTH;
                            settings.macAddress = address;


                            // Print Settings
                            settings.labelNameIndex = LabelInfo.QL700.W62.ordinal();
                            settings.printMode = PrinterInfo.PrintMode.FIT_TO_PAGE;
                            settings.align = PrinterInfo.Align.CENTER;
                            settings.isAutoCut = true;

                            printer.setPrinterInfo(settings);

                            if (printer.startCommunication()) {
                                PrinterStatus bit = printer.printImage(bmpFromBase64(base64));

                                if (bit.errorCode != PrinterInfo.ErrorCode.ERROR_NONE) {
                                    Log.d("TAG", "ERROR - " + bit.errorCode);
                                    result.error("ERROR",bit.errorCode.toString(),"");
                                }
                                printer.endCommunication();
                                result.success(0);
                            }
                        }
                        else if(call.method.equals("getPairedDevices")){

                            List pairList  = new ArrayList<>();

                            List<DiscoveredPrinter> discoveredPrinters = enumerateBluetoothPrinters();

                            for(DiscoveredPrinter dis: discoveredPrinters){

                                try {
                                    pairList.add(dis.toJSON());
                                    Log.d("IN",pairList.get(0).toString());
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            }

                            result.success(pairList.toString());

                        }else{
                            result.notImplemented();
                        }
                    }
                });
    }

    public static Bitmap bmpFromBase64(String base64){
        try{
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            InputStream stream = new ByteArrayInputStream(bytes);

            return BitmapFactory.decodeStream(stream);
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private class DiscoveredPrinter {
        public PrinterInfo.Model model;
        public PrinterInfo.Port port;
        public String modelName;
        public String serNo;
        public String ipAddress;
        public String macAddress;
        public String nodeName;
        public String location;
        public String paperLabelName;

        public DiscoveredPrinter(BluetoothDevice device) {
            port = PrinterInfo.Port.BLUETOOTH;
            ipAddress = null;
            serNo = null;
            nodeName = null;
            location = null;
            macAddress = device.getAddress();
            modelName = device.getName();

            String deviceName = device.getName();
            PrinterInfo.Model[] models = PrinterInfo.Model.values();
            for (PrinterInfo.Model model : models) {
                String modelName = model.toString().replaceAll("_", "-");
                if (deviceName.startsWith(modelName)) {
                    this.model = model;
                    break;
                }
            }
        }



        public DiscoveredPrinter(JSONObject object) throws JSONException {
            model = PrinterInfo.Model.valueOf(object.getString("model"));
            port = PrinterInfo.Port.valueOf(object.getString("port"));

            if (object.has("modelName")) {
                modelName = object.getString("modelName");
            }

            if (object.has("ipAddress")) {
                ipAddress = object.getString("ipAddress");
            }

            if (object.has("macAddress")) {
                macAddress = object.getString("macAddress");
            }

            if (object.has("serialNumber")) {
                serNo = object.getString("serialNumber");
            }

            if (object.has("nodeName")) {
                nodeName = object.getString("nodeName");
            }

            if (object.has("location")) {
                location = object.getString("location");
            }

            if (object.has("paperLabelName")) {
                paperLabelName = object.getString("paperLabelName");
            }

        }

        public JSONObject toJSON() throws JSONException {
            JSONObject result = new JSONObject();
            result.put("model", model.toString());
            result.put("port", port.toString());
            result.put("modelName", modelName);
            result.put("ipAddress", ipAddress);
            result.put("macAddress", macAddress);
            result.put("serialNumber", serNo);
            result.put("nodeName", nodeName);
            result.put("location", location);

            return result;
        }
    }


    private List<DiscoveredPrinter> enumerateBluetoothPrinters() {
        ArrayList<DiscoveredPrinter> results = new ArrayList<DiscoveredPrinter>();
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                return results;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices == null || pairedDevices.size() == 0) {
                return results;
            }

            for (BluetoothDevice device : pairedDevices) {
                DiscoveredPrinter printer = new DiscoveredPrinter(device);

                if (printer.model == null) {
                    continue;
                }
                results.add(printer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

}