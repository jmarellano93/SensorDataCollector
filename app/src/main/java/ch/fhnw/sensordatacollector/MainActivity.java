package ch.fhnw.sensordatacollector;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {

    private SensorManager sensorManager;
    private List<Sensor> selectedSensors = new ArrayList<>();
    private SensorHandler sensorHandler = new SensorHandler();
    private static final String FOLDER_NAME = "SensorDataCollection_Repo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Create external storage directory and init JSON
        File externalFolder = new File(getExternalFilesDir(null), FOLDER_NAME);
        if (!externalFolder.exists()) {
            if (externalFolder.mkdirs()) {
                Log.d("Init", "External folder created: " + externalFolder.getAbsolutePath());
                File initFile = new File(externalFolder, "init_session.json");
                try (FileWriter writer = new FileWriter(initFile)) {
                    writer.write("{\"status\": \"ready\", \"message\": \"External folder initialized.\"}");
                    Log.d("Init", "init_session.json written.");
                } catch (IOException e) {
                    Log.e("Init", "Failed to write init_session.json", e);
                }
            } else {
                Log.e("Init", "Failed to create external folder.");
            }
        }

        // Setup sensor list spinner
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        Spinner sensorSpinner = findViewById(R.id.sensorspinner);
        List<String> sensorNames = new ArrayList<>();
        for (Sensor s : sensorList) sensorNames.add(s.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sensorNames);
        sensorSpinner.setAdapter(adapter);
    }

    public void addButtonClicked(View view) {
        Spinner sensorSpinner = findViewById(R.id.sensorspinner);
        String selectedName = sensorSpinner.getSelectedItem().toString();
        for (Sensor s : selectedSensors) {
            if (s.getName().equals(selectedName)) return;
        }
        Sensor selected = getSensor(selectedName);
        if (selected != null) {
            selectedSensors.add(selected);
            updateSelectedSensorList();
        }
    }

    public void resetButtonClicked(View view) {
        selectedSensors.clear();
        updateSelectedSensorList();
    }

    public void startButtonClicked(View view) {
        EditText patientInput = findViewById(R.id.patientInput);
        EditText experimentInput = findViewById(R.id.experimentInput);
        EditText serverInput = findViewById(R.id.serverInput);

        if (patientInput.getText().toString().trim().isEmpty()) patientInput.setText("0");
        if (experimentInput.getText().toString().trim().isEmpty()) experimentInput.setText("0");

        int patientId = Integer.parseInt(patientInput.getText().toString());
        int experimentId = Integer.parseInt(experimentInput.getText().toString());
        String serverIp = serverInput.getText().toString();

        Button startButton = findViewById(R.id.startbutton);
        Button addButton = findViewById(R.id.addbutton);
        Button resetButton = findViewById(R.id.resetbutton);

        if (startButton.getText().equals("Start")) {
            sensorHandler.setMetaData(patientId, experimentId);
            startCollectingData();
            startButton.setText("Stop");
            patientInput.setEnabled(false);
            experimentInput.setEnabled(false);
            addButton.setEnabled(false);
            resetButton.setEnabled(false);
        } else {
            stopCollectingData(serverIp);
            startButton.setText("Start");
            patientInput.setEnabled(true);
            experimentInput.setEnabled(true);
            addButton.setEnabled(true);
            resetButton.setEnabled(true);
        }
    }

    private void startCollectingData() {
        sensorHandler.getData().clear();
        for (Sensor s : selectedSensors) {
            sensorManager.registerListener(sensorHandler, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopCollectingData(String serverIp) {
        sensorManager.unregisterListener(sensorHandler);
        List<DataObject> collectedData = sensorHandler.getData();

        File jsonFile = new File(getExternalFilesDir(null) + "/" + FOLDER_NAME, "session_data.json");
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(new Gson().toJson(collectedData));
            Log.d("Save", "Data written to external file: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("Save", "Error writing external JSON", e);
        }

        for (DataObject obj : collectedData) {
            uploadData(obj, serverIp);
        }
    }

    private void uploadData(DataObject dObj, String serverIp) {
        RequestQueue queue = Volley.newRequestQueue(this);
        TextView statusView = findViewById(R.id.statusLabel);
        String url = "http://" + serverIp + ":8080/patient/" + dObj.getPatientId() + "/data";

        String json = new Gson().toJson(dObj);
        Log.d("Upload", "Sending to " + url + " payload=" + json);

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> statusView.setText("Upload success."),
                error -> statusView.setText("Upload failed: " + error.getMessage())) {
            @Override
            public String getBodyContentType() { return "application/json"; }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return json.getBytes(StandardCharsets.UTF_8);
            }
        };
        queue.add(request);
    }

    private void updateSelectedSensorList() {
        TextView textView = findViewById(R.id.selectedsensors);
        StringBuilder builder = new StringBuilder();
        for (Sensor s : selectedSensors) builder.append(s.getName()).append("\n");
        textView.setText(builder.toString());
    }

    private Sensor getSensor(String name) {
        for (Sensor s : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }
}
10.207.243.51