package esiot.module_lab_3_2;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.core.http.HttpMethod;

import java.io.IOException;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/*
 * MQTT Agent
 */
public class MQTTAgent extends AbstractVerticle {

	private static final String BROKER_ADDRESS = "broker.mqtt-dashboard.com";
	private final SerialPort serialPort;

	private static final String TOPIC_TEMP = "ESP32_temperature";
	private static final String TOPIC_SAMPLING_FREQUENCY = "ESP32_samplingFrequency";

	private final List<JsonObject> temperatureHistory = new LinkedList<>();
	private static final int MAX_HISTORY_SIZE = 50;

	private float latestTemperature = 0.0f;
	private int samplingFrequency = 5000;
	private int windowTilt = 0;
	private int previousSamplingFrequency = 5000;
	private static String SYSTEM_STATE = "NORMAL";
	private String PREVIOUS_SYSTEM_STATE = "";

	private boolean alarmActive = false;

	public MQTTAgent() {
		serialPort = SerialPort.getCommPort("COM3");
		serialPort.setBaudRate(9600);
		if(!serialPort.openPort()){
			System.out.println("Failed to open serial port.");
		} else {
			System.out.println("Serial port opened successfully.");
		}
	}
	@Override
	public void start() {

		// MQTT Client setup
		log("Starting MQTT agent...");
		MqttClient client = MqttClient.create(vertx);
		client.connect(1883, BROKER_ADDRESS, c -> {
			log("Connected!");
			log("Subscribing!");

			client.publishHandler(s -> {
				String payload = s.payload().toString();
				System.out.println("Received from temperature-monitoring-subsystem: " + payload + " [°C]");
				try {
					latestTemperature = Float.parseFloat(payload);
					JsonObject dataPoint = new JsonObject()
							.put("timestamp", System.currentTimeMillis())
							.put("temperature", latestTemperature);
					temperatureHistory.add(dataPoint);
					if (temperatureHistory.size() > MAX_HISTORY_SIZE) {
						temperatureHistory.remove(0);
					}
					updateSubsystems(client);
				} catch(NumberFormatException e)  {
					System.out.println("Invalid temperature format received: " + payload);
				}
				if(serialPort.isOpen() && !Objects.equals(SYSTEM_STATE, "ALARM")){
                    sendSerialCommand(payload);
                }
			}).subscribe(TOPIC_TEMP, 2);
		});

		Router router = Router.router(vertx);
		router.route().handler(LoggerHandler.create());

		router.route().handler(CorsHandler.create("*")
				.allowedMethod(HttpMethod.GET)
				.allowedMethod(HttpMethod.POST)
				.allowedMethod(HttpMethod.OPTIONS)
				.allowedHeader("Content-Type")
				.allowedHeader("Access-Control-Allow-Origin"));

		router.route("/*").handler(StaticHandler.create("webroot"));

		// GET/POST setup
		router.get("/ESP32_temperature").handler(this::handleGetTemperature);
		router.get("/temperatureHistory").handler(this::handleGetTemperatureHistory);
		router.get("/samplingFrequency").handler(this::handleGetSamplingFrequency);
		router.get("/systemState").handler(this::handleGetSystemState);
		router.get("/windowTilt").handler(this::handleGetWindowTilt);

		router.post("/sendCommand").handler(BodyHandler.create()).handler(this::handleSendCommand);
		router.post("/clearTemperatureHistory").handler(this::handleClearTemperatureHistory);

		vertx.createHttpServer()
				.requestHandler(router)
				.listen(8080, result -> {
					if (result.succeeded()) {
						System.out.println("HTTP server started on port 8080");
					}
					else {
						System.out.println("HTTP server failed to start: " + result.cause());
					}
				});
	}

	private void handleGetTemperature(RoutingContext context) {
		JsonObject response = new JsonObject().put("temperature", latestTemperature);
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(response.encodePrettily());
	}

	private void handleGetTemperatureHistory(RoutingContext context) {
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(new JsonArray(temperatureHistory).encodePrettily());
	}

	private void handleGetSystemState(RoutingContext context) {
		JsonObject response = new JsonObject().put("systemState", SYSTEM_STATE);
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(response.encodePrettily());
	}

	private void handleGetSamplingFrequency(RoutingContext context) {
		JsonObject response = new JsonObject().put("samplingFrequency", samplingFrequency);
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(response.encodePrettily());
	}

	private void handleClearTemperatureHistory(RoutingContext context) {
		temperatureHistory.clear();
		System.out.println("Temperature history cleared.");
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(new JsonObject().put("status", "success").encodePrettily());
	}

	private void handleGetWindowTilt(RoutingContext context) {
		JsonObject response = new JsonObject()
				.put("windowTilt", windowTilt);
		context.response()
				.putHeader("Content-Type", "application/json")
				.end(response.encodePrettily());
	}

	private void handleSendCommand(RoutingContext context) {
		JsonObject body = context.getBodyAsJson();
		if (body != null && body.containsKey("command")) {
			String command = body.getString("command");
            switch (command) {
                case "RESET_ALARM" -> {
                    alarmActive = false;
                    sendSerialCommand("998");
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "success").encodePrettily());
                }
                case "AUTO_MODE" -> {
                    sendSerialCommand("1001");
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "success").encodePrettily());
                }
                case "MANUAL_MODE" -> {
                    sendSerialCommand("1002");
					System.out.println("Sent manual mode command");
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "success").encodePrettily());
                }
            }
		} else {
			context.response()
					.setStatusCode(400)
					.putHeader("Content-Type", "application/json")
					.end(new JsonObject().put("error", "Invalid request body").encodePrettily());
		}
	}

	private void publishSamplingFrequency(MqttClient client) {
		if (samplingFrequency != previousSamplingFrequency) {
			String samplingFrequencyMessage = String.valueOf(samplingFrequency);
			client.publish(TOPIC_SAMPLING_FREQUENCY,
					Buffer.buffer(samplingFrequencyMessage),
					MqttQoS.AT_LEAST_ONCE,
					false,
					false);
			previousSamplingFrequency = samplingFrequency;
			System.out.println("Published new sampling frequency: " + samplingFrequency + " ms");
		}
	}

	private void updateSubsystems(MqttClient client) {
		long currentTime = System.currentTimeMillis();
		// Check for state: ALARM
		boolean alarmFlag = temperatureHistory.stream()
				.filter(dataPoint -> currentTime - dataPoint.getLong("timestamp") <= 3000)
				.allMatch(dataPoint -> dataPoint.getFloat("temperature") >= 30);

		if (alarmFlag && !alarmActive) {
			SYSTEM_STATE = "ALARM";
			samplingFrequency = 500;
			sendSerialCommand("999");
			alarmActive = true;
		} else if (!alarmActive) {
			if (latestTemperature < 25) {
				SYSTEM_STATE = "NORMAL";
				samplingFrequency = 5000;
			} else if (latestTemperature >= 25 && latestTemperature < 30) {
				SYSTEM_STATE = "HOT";
				samplingFrequency = 2000;
			} else if (latestTemperature >= 30) {
				SYSTEM_STATE = "TOO HOT";
				samplingFrequency = 1000;
			}
		}
		if(!SYSTEM_STATE.equals(PREVIOUS_SYSTEM_STATE)){
			System.out.println("Updated system state: " + SYSTEM_STATE);
			PREVIOUS_SYSTEM_STATE = SYSTEM_STATE;
		}
		readSerialCommand(serialPort);
		publishSamplingFrequency(client);
	}

	private void sendSerialCommand(String message) {
		if (serialPort.isOpen()) {
			try {
				serialPort.getOutputStream().write((message + "\n").getBytes());
				System.out.println("Sent to window-controller: " + message);
			} catch (IOException e) {
				System.err.println("Error sending message via Serial: " + e.getMessage());
			}
		} else {
			System.err.println("Serial port is closed.");
		}
	}

	private void readSerialCommand(SerialPort serialPort) {
		new Thread(() -> {
			try (InputStream inputStream = serialPort.getInputStream()) {
				byte[] buffer = new byte[1024];
				while (true) {
					int bytesRead = inputStream.read(buffer);
					if (bytesRead > 0) {
						String receivedData = new String(buffer, 0, bytesRead).trim();
						try  {
							windowTilt = Integer.parseInt(receivedData);
						} catch (NumberFormatException e) {
							System.out.println("Received from window-controller: " + receivedData);
						}
					}
				}
			} catch (IOException e) {
//				System.err.println("Error in Serial listener: " + e.getMessage());
			}
		}).start();
	}

	private void log(String msg) {
		System.out.println("[MQTT AGENT] "+msg);
	}

}