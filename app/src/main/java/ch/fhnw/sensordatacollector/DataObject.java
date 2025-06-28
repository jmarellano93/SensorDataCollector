// File: app/src/main/java/ch/fhnw/sensordatacollector/DataObject.java
package ch.fhnw.sensordatacollector;
import java.util.List;

public class DataObject {
    // No changes to id, device, sensorId, data, timestamp, accuracy, sensorType

    private String experimentId;
    private String patientId;

    // Getter and Setter for experimentId
    public String getExperimentId() {
        return experimentId;
    }
    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    // Getter and Setter for patientId
    public String getPatientId() {
        return patientId;
    }
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    // other getters and setters remain the same
    private Long id;
    private String device;
    private String sensorId;
    private List<Float> data;
    private Long timestamp;
    private Integer accuracy;
    private int sensorType;
    public int getSensorType() {
        return sensorType;
    }

    public void setSensorType(int sensorType) {
        this.sensorType = sensorType;
    }

    public Integer getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Integer accuracy) {
        this.accuracy = accuracy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public List<Float> getData() {
        return data;
    }

    public void setData(List<Float> data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}