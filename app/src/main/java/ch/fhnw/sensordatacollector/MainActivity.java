// File: app/src/main/java/ch/fhnw/sensordatacollector/MainActivity.java
package ch.fhnw.sensordatacollector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {

    private SensorManager sensorManager;
    private final List<Sensor> selectedSensors = new ArrayList<>();
    private final SensorHandler sensorHandler = new SensorHandler();
    private static final String LOG_TAG = "SensorDataCollector";
    // Define the target directory for saving files publicly
    private static final String REPO_DIRECTORY = "SensorDataCollectionRepo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup sensor list spinner
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        Spinner sensorSpinner = findViewById(R.id.sensorspinner);
        List<String> sensorNames = new ArrayList<>();
        // Define the list of required sensors
        List<String> requiredSensorNames = new ArrayList<>();
        requiredSensorNames.add("LSM6DSO Acceleration Sensor");
        requiredSensorNames.add("LSM6DSO Gyroscope Sensor");
        requiredSensorNames.add("Samsung Shake Tracker");
        requiredSensorNames.add("Samsung Rotation Vector");
        requiredSensorNames.add("Motion Sensor");
        requiredSensorNames.add("Gravity Sensor");
        requiredSensorNames.add("Linear Acceleration Sensor");

        for (Sensor s : sensorList) {
            if (requiredSensorNames.contains(s.getName())) {
                sensorNames.add(s.getName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sensorNames);
        sensorSpinner.setAdapter(adapter);

        // Pre-fill the server IP address
        EditText serverInput = findViewById(R.id.serverInput);
        serverInput.setText("192.168.0.168:8080");
    }

    public void addButtonClicked(View view) {
        Spinner sensorSpinner = findViewById(R.id.sensorspinner);
        if (sensorSpinner.getSelectedItem() == null) return; // No sensor selected
        String selectedName = sensorSpinner.getSelectedItem().toString();
        for (Sensor s : selectedSensors) {
            if (s.getName().equals(selectedName)) return; // Avoid duplicates
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

        // Use default values if inputs are empty
        if (patientInput.getText().toString().trim().isEmpty()) patientInput.setText("0");
        if (experimentInput.getText().toString().trim().isEmpty()) experimentInput.setText("0");

        String patientId = patientInput.getText().toString();
        String experimentId = experimentInput.getText().toString();
        String serverIp = serverInput.getText().toString();

        Button startButton = findViewById(R.id.startbutton);
        Button addButton = findViewById(R.id.addbutton);
        Button resetButton = findViewById(R.id.resetbutton);

        if (startButton.getText().equals("Start")) {
            sensorHandler.setMetaData(patientId, experimentId);
            startCollectingData();
            startButton.setText("Stop");
            // Disable UI elements during collection
            patientInput.setEnabled(false);
            experimentInput.setEnabled(false);
            serverInput.setEnabled(false);
            addButton.setEnabled(false);
            resetButton.setEnabled(false);
        } else {
            stopCollectingData(serverIp);
            startButton.setText("Start");
            // Re-enable UI elements after collection
            patientInput.setEnabled(true);
            experimentInput.setEnabled(true);
            serverInput.setEnabled(true);
            addButton.setEnabled(true);
            resetButton.setEnabled(true);
        }
    }

    private void startCollectingData() {
        sensorHandler.getData().clear();
        for (Sensor s : selectedSensors) {
            sensorManager.registerListener(sensorHandler, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Log.d(LOG_TAG, "Started collecting data for " + selectedSensors.size() + " sensors.");
    }

    private void stopCollectingData(String serverIp) {
        sensorManager.unregisterListener(sensorHandler);
        List<DataObject> collectedData = sensorHandler.getData();
        Log.d(LOG_TAG, "Stopped collecting data. Total points: " + collectedData.size());

        if (collectedData.isEmpty()) {
            Log.w(LOG_TAG, "No data collected. Skipping save and upload.");
            return;
        }

        // 1. Save the collected data to a public Documents folder
        saveDataToPublicDocuments(collectedData);

        // 2. Upload the entire batch of data to the server
        uploadBulkData(collectedData, serverIp);
    }

    private void saveDataToPublicDocuments(List<DataObject> dataToSave) {
        // Use Gson to convert the list of DataObjects into a single JSON array string
        Gson gson = new Gson();
        String jsonPayload = gson.toJson(dataToSave);

        // Create a timestamped filename for uniqueness
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "sensor_data_" + timeStamp + ".json";

        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        // Place the file in the 'Documents/SensorDataCollectionRepo' directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + REPO_DIRECTORY);
        }

        Uri collectionUri = MediaStore.Files.getContentUri("external");
        Uri fileUri = resolver.insert(collectionUri, contentValues);

        if (fileUri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(fileUri)) {
                if (outputStream != null) {
                    outputStream.write(jsonPayload.getBytes());
                    // **FIXED**: Corrected LOG_TA to LOG_TAG
                    Log.d(LOG_TAG, "Successfully saved data to: " + fileUri);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to save data to public documents.", e);
            }
        } else {
            Log.e(LOG_TAG, "Failed to create new MediaStore entry for the file.");
        }
    }

    private void uploadBulkData(List<DataObject> data, String serverIp) {
        if (data.isEmpty()) return;

        RequestQueue queue = Volley.newRequestQueue(this);
        TextView statusView = findViewById(R.id.statusLabel);

        String patientId = data.get(0).getPatientId();
        String experimentId = data.get(0).getExperimentId();

        String url = "http://" + serverIp + "/patient/" + patientId + "/data/bulk";

        // **MODIFIED**: Create a JSON object that wraps the data array and includes the experimentId
        final String requestBody;
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("experimentId", experimentId);
            // Convert the list of data objects to a JSON array
            Gson gson = new Gson();
            JSONArray dataArray = new JSONArray(gson.toJson(data));
            jsonBody.put("data", dataArray);
            requestBody = jsonBody.toString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to create JSON body for bulk upload", e);
            statusView.setText("Error: Failed to build JSON request");
            return;
        }

        Log.d(LOG_TAG, "Uploading bulk data to " + url);
        Log.d(LOG_TAG, "Payload: " + requestBody);

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d(LOG_TAG, "Upload success: " + response);
                    statusView.setText("Upload success.");
                },
                error -> {
                    String errorMsg = "Upload failed: ";
                    if (error.networkResponse != null) {
                        errorMsg += "Status " + error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(LOG_TAG, "Error Response: " + responseBody);
                            errorMsg += "\n" + responseBody;
                        } catch (Exception e) {
                            // Ignore
                        }
                    } else {
                        errorMsg += error.getMessage();
                    }
                    Log.e(LOG_TAG, errorMsg, error);
                    statusView.setText(errorMsg);
                }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return requestBody.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                // Add the HTTP Basic Authentication header
                String credentials = "user:password"; // Hardcoded credentials as per backend
                String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
                headers.put("Authorization", auth);
                return headers;
            }
        };
        // **FIXED**: Corrected the typo "queue..add" to "queue.add"
        queue.add(request);
    }

    private void updateSelectedSensorList() {
        TextView textView = findViewById(R.id.selectedsensors);
        StringBuilder builder = new StringBuilder();
        for (Sensor s : selectedSensors) {
            builder.append(s.getName()).append("\n");
        }
        textView.setText(builder.toString());
    }

    private Sensor getSensor(String name) {
        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : allSensors) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }
}