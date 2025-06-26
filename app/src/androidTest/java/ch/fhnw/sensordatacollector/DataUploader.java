package ch.fhnw.sensordatacollector;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DataUploader {

    public static void uploadSensorData(Context context, String patientId, String experimentId,
                                        String sensorType, float x, float y, float z) {

        String url = "http://10.0.2.2:8080/patient/" + patientId + "/data"; // Use your actual IP on physical device

        JSONObject dataJson = new JSONObject();
        try {
            dataJson.put("experimentId", experimentId);
            dataJson.put("sensorType", sensorType);
            dataJson.put("timestamp", System.currentTimeMillis());
            dataJson.put("valueX", x);
            dataJson.put("valueY", y);
            dataJson.put("valueZ", z);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest jsonRequest = new JsonObjectRequest(
                Request.Method.POST, url, dataJson,
                response -> Log.d("Upload", "Upload successful: " + response.toString()),
                error -> Log.e("Upload", "Upload failed: " + error.toString())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                String credentials = "user:password";
                String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
                headers.put("Authorization", auth);
                return headers;
            }
        };

        queue.add(jsonRequest);
    }
}
