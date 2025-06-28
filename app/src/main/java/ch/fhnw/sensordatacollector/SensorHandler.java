// File: app/src/main/java/ch/fhnw/sensordatacollector/SensorHandler.java
package ch.fhnw.sensordatacollector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import java.util.ArrayList;
import java.util.List;

public class SensorHandler implements SensorEventListener {

    private final List<DataObject> data = new ArrayList<>();
    private String patientId;
    private String experimentId;

    public SensorHandler() {
    }

    public void setMetaData(String patientId, String experimentId) {
        this.patientId = patientId;
        this.experimentId = experimentId;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        DataObject dObj = new DataObject();
        dObj.setPatientId(patientId);
        dObj.setExperimentId(experimentId);
        dObj.setSensorType(sensorEvent.sensor.getType());
        dObj.setSensorId(sensorEvent.sensor.getName());
        List<Float> dataValues = new ArrayList<>();

        if (sensorEvent.values!= null) {
            for (float x : sensorEvent.values) {
                dataValues.add(x);
            }
        }

        dObj.setData(dataValues);
        dObj.setTimestamp(sensorEvent.timestamp);
        dObj.setAccuracy(sensorEvent.accuracy);
        this.data.add(dObj);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // Method can be left empty if not needed
    }

    public List<DataObject> getData() {
        return this.data;
    }
}