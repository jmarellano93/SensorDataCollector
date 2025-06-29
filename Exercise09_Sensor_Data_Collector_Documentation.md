# The SensorDataCollector Android Application

### Android Studio Github Repository: https://github.com/jmarellano93/SensorDataCollector

## Section I: System Overview

This document provides a comprehensive technical guide for the SensorDataCollector Android application. The analysis is based on a full review of the application's source code repository. The purpose of this guide is to equip developers, researchers, and technical staff with the knowledge required to build, deploy, operate, and extend the application and its associated data collection ecosystem. It covers the system's architecture, operational procedures, data models, and the necessary steps for environment setup and data verification.

### I.1) Purpose and Scope

The SensorDataCollector application is a specialized tool designed for research and data-gathering activities. Its primary function is to capture time-series data from a variety of hardware sensors available on a standard Android device. The application is built to support structured experimental protocols by enabling the user to associate each data collection session with specific metadata tags, namely a patientId and an experimentId. This feature makes it particularly well-suited for scenarios such as clinical studies, biomechanical analysis, or any research field that requires correlating sensor readings with specific subjects and experimental conditions.

The scope of the application is focused entirely on the client-side data collection process. It provides the interface for initiating and controlling the recording, the logic for capturing sensor events, and the mechanisms for persisting the collected data. It is designed to operate in conjunction with a backend server, which is responsible for receiving and storing the data remotely. The backend service itself is outside the scope of this application's codebase and must be provided by the user.

### I.2) Core Functionality

The application's design centers on three core functionalities that together create a robust data collection workflow:

- Selective Sensor Activation: The user is presented with a list of hardware sensors available on the device, filtered to include those most relevant for motion and orientation tracking, such as accelerometers and gyroscopes. The user can then select one or more of these sensors for data recording during a session. This selective approach allows researchers to tailor data collection to the specific needs of an experiment, reducing the volume of irrelevant data and focusing on the metrics of interest.
- Metadata Association: Before initiating a collection session, the user can input identifiers for the patient (or subject) and the specific experiment. These identifiers are then programmatically attached to every single data point recorded during that session. This ensures that all collected data is properly contextualized, which is a fundamental requirement for subsequent analysis and for maintaining data provenance in research settings.
- Dual-Destination Data Persistence: A key architectural feature of the application is its two-pronged approach to data persistence, which is designed to maximize data integrity and fault tolerance. Upon the completion of a collection session, the application performs two distinct actions:
    - Local Storage: The entire batch of collected sensor data is serialized into a single, timestamped JSON file. This file is saved to a publicly accessible directory on the Android device's storage.
    - Remote Upload: Concurrently, the same batch of data is packaged into a JSON payload and transmitted via an HTTP POST request to a user-configured backend server.

This dual-persistence model is a deliberate design choice that enhances the system's robustness. By first securing a local copy of the data before attempting a network upload, the application mitigates the risk of data loss due to network interruptions, server unavailability, or other transient communication failures. This makes it a reliable tool for fieldwork or any environment where stable network connectivity cannot be guaranteed.

### I.3) High-Level Data Workflow

The lifecycle of data within the SensorDataCollector application follows a clear and logical progression, from initial configuration to final storage. The process can be summarized in the following sequence:

- Configuration: The user launches the application and populates the UI fields with the patientId, experimentId, and the network address (IP:port) of the backend data collection API service. The user then selects the desired sensors for the session.
- Initiation: The user clicks the "Start" button. The MainActivity captures the configured metadata and passes it to an instance of the SensorHandler class. It then registers this handler with the Android SensorManager to begin listening for events from the selected sensors.
- Collection: As the user performs the experimental activity, the device's sensors generate a continuous stream of data. The Android operating system forwards these events to the SensorHandler. For each event, the handler creates a DataObject instance, populates it with the sensor readings and the session metadata, and adds it to an internal list.
- Termination: The user clicks the "Stop" button. The MainActivity immediately unregisters the SensorHandler from the SensorManager, ceasing all data collection.
- Persistence and Upload: The MainActivity retrieves the complete list of DataObjects from the SensorHandler. It then orchestrates the dual-persistence process: it first calls the saveDataToPublicDocuments method to write the data to a local JSON file, and subsequently calls the uploadBulkData method to transmit the data to the remote server. The user receives a status message in the UI indicating the outcome of the upload attempt.

## Section II: Architectural Deep Dive

This section dissects the primary components, data flow, security posture, and networking implementation to provide a comprehensive understanding of the system's internal workings.

### II.1) Core Component Analysis

The application's functionality is distributed across three principal Java classes, each with a distinct and focused responsibility.

- MainActivity.java (The Orchestrator): This class is the central hub of the application. It serves as the controller in a Model-View-Controller (MVC) pattern, bridging the user interface with the underlying business logic. Its responsibilities include:
    - Rendering the user interface defined in the activity_main.xml layout file.
    - Handling all user interactions, such as button clicks (addButtonClicked, resetButtonClicked, startButtonClicked) and text input from the EditText fields.
    - Managing the application's state, toggling between an "idle" state and a "collecting" state, and enabling or disabling UI components accordingly.
    - Instantiating and configuring the SensorHandler with the necessary metadata.
    - Orchestrating the final data persistence and upload processes when the user stops a collection session. It retrieves the collected data and initiates both the local file save and the remote API call.

- SensorHandler.java (The Collector): This class is a specialized component designed exclusively for data collection. It implements the SensorEventListener interface, making it the designated recipient of sensor data from the Android operating system. Its responsibilities are narrowly focused:
    - To listen for new sensor readings via the onSensorChanged callback method.
    - Upon receiving a SensorEvent, to create a new DataObject instance.
    - To populate this DataObject with the event's raw values (sensorEvent.values), timestamp, and accuracy, as well as the patientId and experimentId that were passed to it during its initialization.
    - To accumulate these populated DataObject instances in an internal ArrayList.
    - This clean separation of concerns is a notable strength of the architecture. By isolating the data collection logic within SensorHandler, the MainActivity is freed from the complexities of interacting with the sensor framework. This modular design makes the code easier to maintain, debug, and extend. For instance, if data transformation or real-time filtering logic were required, the changes could be confined to the SensorHandler without impacting the UI or networking code.

- DataObject.java (The Data Model): This class is a Plain Old Java Object (POJO) that serves as the canonical data structure for a single sensor reading. It defines the schema for the data being collected. Each instance of
  DataObject encapsulates not just the raw sensor values (a list of floats) but also the complete context surrounding that reading, including the patientId, experimentId, sensorId, sensorType, timestamp, and accuracy. This structured model is fundamental to the system's ability to produce clean, well-organized, and analysis-ready data. It forms the basis for the JSON serialization, ensuring that both the local file and the remote API payload are consistent and self-describing.

### II.2) Data Flow and Lifecycle

The flow of data through the application follows a precise, event-driven lifecycle managed by the core components:
- Initialization: The user populates the patientId, experimentId, and serverIp fields in the UI presented by MainActivity.
- Activation: The user clicks the "Start" button. The startButtonClicked method in MainActivity is invoked. It reads the metadata from the UI, calls sensorHandler.setMetaData(...), and then registers the sensorHandler instance as a listener with the Android SensorManager for each of the user-selected sensors.
- Collection: The Android OS begins invoking the onSensorChanged method within the sensorHandler instance whenever a new reading is available from a registered sensor. Each invocation results in the creation and population of a DataObject, which is then added to the sensorHandler's internal data list. This process continues until the collection is stopped.
- Termination: The user clicks the "Stop" button. The startButtonClicked method in MainActivity is again invoked. It detects that the system is in a "collecting" state, and proceeds to call sensorManager.unregisterListener(sensorHandler), which halts the flow of sensor events.
- Persistence: MainActivity immediately retrieves the complete list of DataObjects by calling sensorHandler.getData(). It then passes this list to the saveDataToPublicDocuments method. This method uses the Google Gson library to serialize the entire list into a JSON array string and saves it to a new file in the device's public Documents/SensorDataCollectionRepo directory.
- Transmission: Following the local save, MainActivity passes the data list to the uploadBulkData method. This method constructs a new JSON object that wraps the data list within a "data" key and includes the experimentId at the top level. It then uses the Volley library to send this JSON payload in an HTTP POST request to the server endpoint configured by the user.

### II.3) Manifest, Permissions, and Security Posture

The AndroidManifest.xml file defines the application's core properties, required permissions, and overall security configuration.

- Permissions: The application requests two network-related permissions:
    - android.permission.INTERNET: This is essential for allowing the application to open network sockets and communicate with the remote backend server.
    - android.permission.ACCESS_NETWORK_STATE: This allows the app to check the status of network connectivity, though this capability is not actively used in the current implementation.
- Security Configuration and Posture: The manifest and source code contain several indicators that define the application's security posture as that of a prototype intended for trusted, non-production environments only.
    - android:usesCleartextTraffic="true": This flag in the manifest is a critical setting. Modern Android versions block unencrypted HTTP traffic by default to enforce better security practices. The inclusion of this flag is a deliberate action to disable this protection, allowing the app to communicate with a simple HTTP server instead of a secure HTTPS server.
    - android:debuggable="true": This flag enables debugging and exposes the application to potential security risks. While necessary for development, it should always be set to false for any release build.
    - Hardcoded Credentials: The most significant security issue resides in MainActivity.java, where authentication credentials are hardcoded directly into the source: String credentials = "user:password";. This practice is extremely insecure, as anyone with access to the application's compiled code can easily extract these credentials.

The combination of these three factors—permitting unencrypted traffic, enabling debug mode, and hardcoding credentials—unequivocally indicates that the application is not designed for use with sensitive data or in untrusted network environments. Any deployment beyond a controlled local development or research setup would require a complete re-architecture of its security and authentication mechanisms.

### II.4) Networking and API Communication

All network communication is managed using the Volley library, a lightweight HTTP library provided by Google for Android development. The implementation is contained within the uploadBulkData method in MainActivity.java.

The communication process involves creating a StringRequest for a POST operation. The request body is manually constructed as a JSON string. The method overrides Volley's default behaviors to provide custom headers and the request body:
- getBodyContentType(): This is overridden to return "application/json; charset=utf-8", correctly identifying the payload type to the server.
- getBody(): This is overridden to provide the JSON payload as a byte array.
- getHeaders(): This is overridden to insert the necessary HTTP headers, most importantly the Authorization header for HTTP Basic Authentication, which contains the Base64-encoded hardcoded credentials.

### II.5) Architectural Anomaly: The DataUploader.java Utility

A review of the project's file structure reveals a file named DataUploader.java located in the app/src/androidTest/java/ directory. It is essential to recognize that this file's location places it within the source set for **instrumented tests**, not the main application itself.

This DataUploader.java class contains a static method, uploadSensorData, which is designed to upload a single sensor data point to the server. This stands in stark contrast to the uploadBulkData method in MainActivity, which uploads an entire session's worth of data in one batch.
This file is a testing utility, created to test the backend's API endpoint for receiving single data entries, a common practice during API development. It is never called by the main application code in MainActivity and plays no role in the application's standard operational workflow. Developers working on the main application should consider this file a "red herring" and should not attempt to modify or integrate it into the primary data collection logic.

## Section III: Environment Setup and First-Time Build

This section provides a detailed, step-by-step guide for setting up a development environment, building the SensorDataCollector application from its source code, and configuring the necessary network and backend components for successful operation.

### III.1) Prerequisites

Before beginning, ensure the following software and hardware are available and correctly installed on your development machine and test device.

- Software:
    - Android Studio: The latest stable version is highly recommended. It provides the IDE, build tools (Gradle), and SDK management required for the project.
    - Android SDK: Android Studio will manage the installation of the required SDK platforms. The project is configured to compile against modern Android versions.
    - Git: A version control client is necessary to clone the jmarellano93-sensordatacollector repository from its source.
    - Postman API Platform: This tool is essential for testing the backend API and verifying that data has been successfully uploaded, as detailed in Section 6.
- Hardware:
    - Android Device: A physical Android smartphone or tablet equipped with the hardware sensors you intend to collect data from (e.g., accelerometer, gyroscope). While an emulator can be used, a physical device provides real-world sensor data. The device must have Developer Options and USB Debugging enabled.
    - Development Machine: A computer (Windows, macOS, or Linux) capable of running Android Studio and the backend service application.

### III.2) Building the Application from Source

Follow these steps to compile and run the application on your Android device:

- Clone the Repository: Open a terminal or command prompt and use Git to clone the project source code to your local machine.
- Open in Android Studio: Launch Android Studio and select "Open" from the welcome screen or "File > Open" from the menu. Navigate to and select the cloned jmarellano93-sensordatacollector project directory.
- Gradle Sync: Android Studio will automatically initiate a Gradle sync process. This may take a few minutes as Gradle downloads the specific version of the Gradle wrapper defined in gradle-wrapper.properties and all project dependencies declared in the build files. Monitor the "Build" panel for progress and ensure the sync completes successfully.
- Connect Device: Connect your Android device to the development machine via a USB cable. Ensure the device is unlocked and that you have authorized the USB debugging connection when prompted on the device screen.
- Build and Run: In the Android Studio toolbar, select your connected device from the dropdown menu of available devices. Click the "Run 'app'" button (the green play icon). Android Studio will compile the application, generate an APK, install it on your device, and launch the MainActivity.

### III.3) Backend Service Configuration (User Responsibility)

It is critical to understand that the SensorDataCollector repository contains the client application only. It does not include a backend server application. The user is responsible for providing, deploying, and running a compatible backend service.
This backend service must expose a RESTful API that adheres to the contract expected by the application. The specific details of this API contract, including the required endpoint, HTTP method, and JSON payload structure, are formally defined in Section 5 of this document. The service must be running and accessible over the local network before any data collection and upload can be attempted.

### III.4) Critical Network Configuration

Correct network configuration is the most common point of failure when setting up a client-server mobile application for local development. The following steps are non-negotiable for the SensorDataCollector app to communicate with your backend service.

- Establish a Shared Network: The Android device and the development machine that is hosting the backend service must be connected to the same Wi-Fi network. This allows them to communicate directly using local IP addresses.
- Determine the Server's Local IP Address: You must find the local IP address of your development machine. This address will be entered into the "Server IP Address" field in the application.
    - On Windows: Open Command Prompt and type ipconfig. Look for the "IPv4 Address" under your active Wi-Fi adapter.
    - On macOS or Linux: Open Terminal and type ifconfig or ip addr. Look for the "inet" address associated with your Wi-Fi interface (e.g., en0, wlan0). The IP address will typically be in a private range, such as 192.168.x.x, 10.x.x.x, or 172.16.x.x. The IP address 192.168.0.168 pre-filled in the app's UI is a placeholder from the original developer's network and will not work on your network. It must be replaced.
- Configure Firewall Rules: By default, your development machine's operating system firewall may block incoming network connections. You must configure the firewall to allow incoming traffic on the specific port your backend service is listening on. For example, if your service runs on port 8080 (as suggested by the default text in the app), you must create a new inbound rule for TCP port 8080.

Failure to correctly perform these network configuration steps will result in the application being unable to connect to the server, and uploads will fail, likely with a connection timeout error.

## Section IV: Application Operational Walkthrough

This section serves as a practical user manual for operating the SensorDataCollector application. It details the user interface and provides a step-by-step protocol for conducting a complete data collection session, from initial setup to final data persistence.

### IV.1) User Interface Overview

The application's main screen is defined by the activity_main.xml layout file and provides a simple, functional interface for controlling the data collection process. The UI is composed of the following key elements:

- Input Fields:
    - Patient ID: An EditText field for entering the subject or patient identifier. If left empty, the application will use a default value of "0".
    - Experiment ID: An EditText field for entering the identifier for the specific session or trial. This also defaults to "0" if left empty.
    - Server IP Address: A critical EditText field where the user must enter the correct local IP address and port of the running backend service (e.g., 192.168.1.10:8080).
- Sensor Selection:
    - Spinner: A dropdown menu that is populated with a list of available hardware sensors on the device.
    - Add Button: After selecting a sensor from the spinner, clicking this button adds it to the list of sensors to be recorded.
    - Reset Button: This button clears the current list of selected sensors.
- Execution Control:
    - Start/Stop Button: This is the primary control button. Its text and function toggle based on the application's state. Initially, it reads "Start." When clicked, it begins data collection and its text changes to "Stop."
- Status Display:
    - Status Label: A TextView that provides feedback to the user, such as the success or failure of a data upload.
    - Selected Sensors List: A TextView at the bottom of the screen that displays the names of all sensors that have been added for the current session.

### IV.2) Step-by-Step Data Collection Protocol

To perform a data collection session, follow this precise protocol:

Launch the Application: Open the SensorDataCollector app on your configured Android device.

- Configure the Session:
    - In the "Patient ID" field, enter a unique identifier for the subject of the experiment.
    - In the "Experiment ID" field, enter a unique identifier for this specific data collection trial.
    - Crucially, in the "Server IP Address" field, delete the default text and enter the correct local IP address and port of your backend service, which you determined during the environment setup (Section 3.4).
- Select the Desired Sensors:
    - Tap the dropdown menu to view the list of available sensors.
    - Select a sensor you wish to record (e.g., "LSM6DSO Acceleration Sensor").
    - Tap the "Add" button. The sensor's name will appear in the "Selected Sensors" list at the bottom of the screen.
    - Repeat this process for all other sensors you wish to include in the recording.
    - If you make a mistake, tap the "Reset" button to clear the list and start over.
- Start Data Collection:
    - Once all settings are configured and sensors are selected, tap the "Start" button.
    - The button's text will change to "Stop," and all input fields and sensor selection buttons will become disabled to prevent changes during the recording.
- Perform the Experimental Activity:
    - Carry out the activity that you intend to measure (e.g., have the subject walk, run, shake the device, etc.). The application is now passively recording data from the selected sensors in the background.
- Stop Data Collection:
    - When the activity is complete, tap the "Stop" button.
    - The application will immediately stop recording. It will then save the data to a local file and attempt to upload it to the server.
    - Observe the "Status" label for a message indicating the outcome of the upload (e.g., "Upload success." or an error message).
    - The UI controls will be re-enabled, ready for another session.

### IV.3) Verifying Locally Stored Data

As part of its dual-persistence strategy, the application saves a complete copy of the collected data on the device. You can verify the presence and content of this file.

- Location: The JSON file is saved in a public directory. Using a file manager application on your Android device, navigate to the following path: Internal Storage > Documents > SensorDataCollectionRepo.
- Filename: The file is given a unique, timestamped name to prevent overwriting previous sessions. The format is sensor_data_yyyyMMdd_HHmmss.json, for example, sensor_data_20231027_143000.json.
- Content: You can open this file with a text editor to inspect its contents. It will contain a JSON array where each element is a complete DataObject representing a single sensor reading from your session.

## Section V: Data Models and API Specification

This section provides a formal technical reference for the data structures and application programming interface (API) contract used by the SensorDataCollector system. This information is essential for developers who need to build a compatible backend service or work with the data generated by the application.

### V.1) The DataObject Data Model

The fundamental unit of data in the system is the DataObject. This Java class defines the schema for every sensor event recorded. The table below details each field within the DataObject class, its data type, and its source within the application's code.

**Table 5.1: DataObject Field Descriptions**

| Field Name | Data Type | Description | Source |
| :--- | :--- | :--- | :--- |
| patientId | String | The user-defined identifier for the patient or subject. | Populated in SensorHandler.java from UI input. |
| experimentId | String | The user-defined identifier for the specific experiment or session. | Populated in SensorHandler.java from UI input. |
| sensorType | int | The integer type constant for the sensor, as defined in the Android Sensor class (e.g., Sensor.TYPE_ACCELEROMETER which is 1). | Sourced from sensorEvent.sensor.getType(). |
| sensorId | String | The unique string name of the hardware sensor as reported by the Android OS. | Sourced from sensorEvent.sensor.getName(). |
| data | List<Float> | A list of float values from the sensor event. The number of values depends on the sensor type (e.g., 3 values for accelerometer: X, Y, Z). | Sourced from sensorEvent.values. |
| timestamp | Long | The timestamp of the event in nanoseconds since the device last booted. This is a high-precision, monotonic clock value. | Sourced from sensorEvent.timestamp. |
| accuracy | Integer | An integer representing the accuracy level of the sensor reading (e.g., SensorManager.SENSOR_STATUS_ACCURACY_HIGH). | Sourced from sensorEvent.accuracy. |
| device | String | (Unused) This field exists in the DataObject class but is never populated by the current application code. It is intended to store the device model name. | Defined in DataObject.java. |
| id | Long | (Unused) This field exists in the DataObject class but is never populated. It is likely intended to be used as a unique database primary key on the server side. | Defined in DataObject.java. |

### V.2) Backend API Specification (The "Contract")

This section defines the "contract" that a user-provided backend must fulfill to be compatible with the SensorDataCollector application.

**Table 5.2: API Endpoint Specifications**

| Endpoint                  | HTTP Method | URL Structure                    | Headers                                                                 | Request Body    | Description                                                                                                                                                                                                                     |
|---------------------------|-------------|----------------------------------|-------------------------------------------------------------------------|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Bulk Data Upload (Required) | POST        | `/patient/{patientId}/data/bulk` | `Content-Type: application/json`<br>`Authorization: Basic dXNlcjpwYXNzd29yZA==` | See Section 5.3 | This is the primary endpoint that the application uses to upload the complete batch of data collected during a session. The `{patientId}` in the URL corresponds to the value entered in the UI. The Base64 string in the Authorization header corresponds to `"user:password"`. |
| Data Retrieval (Recommended) | GET         | `/patient/{patientId}/data`       | `Authorization: Basic dXNlcjpwYXNzd29yZA==`                            | N/A              | A recommended endpoint for retrieving data for a specific patient. This endpoint is not used by the app but is essential for data verification using tools like Postman. It can be enhanced with query parameters (e.g., `?experimentId=...`) for more granular filtering. |

### V.3) JSON Payload Structures

The application generates JSON in two slightly different formats depending on the destination. A backend developer must be aware of this distinction.

- Local File Format (sensor_data_...json): The file saved locally on the device is a direct serialization of the List<DataObject>. It is a JSON array where each object is a complete DataObject.
  ,
  "timestamp": 1234567890123,
  "accuracy": 3
  },
  {
  "patientId": "1",
  "experimentId": "101",
  "sensorType": 1,
  "sensorId": "LSM6DSO Acceleration Sensor",
  "data": [ -0.11, 9.81, 0.49 ],
  "timestamp": 1234567990123,
  "accuracy": 3
  }
  ]
```
        API POST Request Body Format:
        The payload sent to the backend API is a JSON object that wraps the data array. This structure is defined in the uploadBulkData method in MainActivity.java. The
        experimentId is elevated to a top-level field, and the list of DataObjects is nested under a data key.
        JSON
        {
          "experimentId": "101",
          "data":,
              "timestamp": 1234567890123,
              "accuracy": 3
            },
            {
              "patientId": "1",
              "experimentId": "101",
              "sensorType": 1,
              "sensorId": "LSM6DSO Acceleration Sensor",
              "data": [ -0.11, 9.81, 0.49 ],
              "timestamp": 1234567990123,
              "accuracy": 3
            }
          ]
        }
```

## Section VI: Data Verification via Postman

A critical part of the data collection process is verifying that the data has been successfully received and correctly stored by the backend service. The Postman API Platform is the ideal tool for this task. This section provides a step-by-step guide to using Postman to query the backend and validate the uploaded data. This process serves as the final, confirmatory step of the end-to-end data pipeline, providing definitive proof that the entire system is functioning as expected.

### VI.1) Postman Workspace Setup

To maintain an organized workflow, it is best practice to set up a dedicated collection and environment in Postman.

- Create a Collection: In Postman, create a new Collection named "Sensor Data API". This will house all the requests related to your backend service.
- Configure Environment Variables: Create a new Environment in Postman named "SensorData Local Dev". Within this environment, create at least two variables:
    - server_url: Set this to the base URL of your backend service (e.g., http://192.168.1.10:8080).
    - patient_id: Set this to the ID of a patient whose data you want to query (e.g., 1). Using environment variables makes your requests reusable and easy to adapt to different servers or subjects.

### VI.2) Building and Executing a GET Request

This guide assumes your backend implements the recommended GET endpoint from Section 5.2.

- Create a New Request: Within your "Sensor Data API" collection, create a new request and name it "Get Data by Patient".
- Set HTTP Method and URL:
    - Set the HTTP method to GET from the dropdown menu.
    - In the URL field, use the environment variables you created: {{server_url}}/patient/{{patient_id}}/data. Postman will substitute these with the values from your active environment.
- Configure Authorization:
    - Navigate to the "Authorization" tab for the request.
    - From the "Type" dropdown, select "Basic Auth".
    - Postman will display fields for "Username" and "Password". Enter user and password, respectively. These must match the credentials hardcoded in the SensorDataCollector application.
- Send the Request: Ensure your "SensorData Local Dev" environment is selected in the top-right corner of Postman. Click the "Send" button to execute the request to your backend service.

### VI.3) Analyzing the API Response

After sending the request, Postman will display the server's response in the bottom panel.

- Successful Response: A successful request should result in a 200 OK status code. The response body should contain a JSON array of the DataObjects that were previously uploaded from the Android device for the specified patient.
- Data Validation: Carefully inspect the JSON in the response body.
    - Confirm that the patientId in each object matches the one you requested.
    - Check if the experimentId and sensor data values correspond to a known collection session you performed.
    - Verify that the structure of the objects matches the DataObject model defined in Section 5.1.
- Troubleshooting Common Errors:
    - 404 Not Found: This typically means the URL is incorrect or the backend endpoint does not exist. Double-check your server_url environment variable and the endpoint path.
    - 401 Unauthorized: This indicates an authentication failure. Verify that you have correctly configured Basic Auth in Postman with the user:password credentials.
    - 500 Internal Server Error: This error originates from your backend service itself, indicating a problem with its own code. You will need to check the logs of your server application to diagnose the issue.
    - Connection Error: If Postman cannot connect at all, verify that your backend service is running and that your machine's firewall is not blocking the connection on the specified port.
