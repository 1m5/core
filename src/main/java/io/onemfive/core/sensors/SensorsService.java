package io.onemfive.core.sensors;

import io.onemfive.core.sensors.clearnet.ClearnetSensor;
import io.onemfive.core.sensors.i2p.bote.I2PBoteSensor;
import io.onemfive.core.sensors.tor.TorSensor;
import io.onemfive.core.util.AppThread;
import io.onemfive.core.BaseService;
import io.onemfive.core.Config;
import io.onemfive.core.MessageProducer;
import io.onemfive.core.sensors.i2p.I2PSensor;
import io.onemfive.core.sensors.mesh.MeshSensor;
import io.onemfive.data.Envelope;
import io.onemfive.data.Route;
import io.onemfive.data.util.DLC;
import io.onemfive.data.util.JSONParser;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the main entry point into the application by supported networks.
 * It registers all supported/configured Sensors and manages their lifecycle.
 *
 *  @author ObjectOrange
 */
public class SensorsService extends BaseService {

    private static final Logger LOG = Logger.getLogger(SensorsService.class.getName());

    public static final String OPERATION_GET_KEYS = "GET_KEYS";
    public static final String OPERATION_SEND = "SEND";
    public static final String OPERATION_REPLY_CLEARNET = "REPLY_CLEARNET";

    private Properties config;
    private Map<String, Sensor> registeredSensors;
    private Map<String, Sensor> activeSensors;

    public SensorsService(MessageProducer producer) {
        super(producer);
    }

    @Override
    public void handleDocument(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleEvent(Envelope envelope) {
        handleAll(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        handleAll(envelope);
    }

    private void handleAll(Envelope e) {
        Route r = e.getRoute();
        Sensor sensor = null;
        if(Envelope.Sensitivity.MEDIUM.equals(e.getSensitivity())
                || r.getOperation().endsWith(".onion")
                || (e.getURL() != null && e.getURL().getProtocol() != null && e.getURL().getProtocol().endsWith(".onion"))
                && activeSensors.containsKey(TorSensor.class.getName())) {
            // Use Tor
            LOG.info("Using Tor Sensor...");
            sensor = activeSensors.get(TorSensor.class.getName());
        } else if(Envelope.Sensitivity.HIGH.equals(e.getSensitivity())
                || r.getOperation().endsWith(".i2p")
                || (e.getURL() != null && e.getURL().getProtocol() != null && e.getURL().getProtocol().endsWith(".i2p"))
                && activeSensors.containsKey(I2PSensor.class.getName())) {
            // Use I2P
            LOG.info("Using I2P Sensor...");
            sensor = activeSensors.get(I2PSensor.class.getName());
        } else if(Envelope.Sensitivity.VERYHIGH.equals(e.getSensitivity())
                || r.getOperation().endsWith(".bote")
                || (e.getURL() != null && e.getURL().getProtocol() != null && e.getURL().getProtocol().endsWith(".bote"))
                && activeSensors.containsKey(I2PBoteSensor.class.getName())) {
            // Use I2P Bote
            LOG.info("Using I2P Bote Sensor...");
            sensor = activeSensors.get(I2PBoteSensor.class.getName());
        } else if(Envelope.Sensitivity.EXTREME.equals(e.getSensitivity())
                || r.getOperation().endsWith(".mesh")
                || (e.getURL() != null && e.getURL().getProtocol() != null && e.getURL().getProtocol().endsWith(".mesh"))
                && activeSensors.containsKey(MeshSensor.class.getName())) {
            // Use Mesh
            LOG.info("Using Mesh Sensor...");
            sensor = activeSensors.get(MeshSensor.class.getName());
        } else if(Envelope.Sensitivity.NONE.equals(e.getSensitivity())
                || Envelope.Sensitivity.LOW.equals(e.getSensitivity())
                || r.getOperation().startsWith("http")
                || e.getURL() != null && e.getURL().getProtocol() != null && e.getURL().getProtocol().startsWith("http")) {
            // Use Clearnet
            LOG.info("Using Clearnet Sensor...");
            sensor = activeSensors.get(ClearnetSensor.class.getName());
        }

        if(sensor != null) {
            if(OPERATION_SEND.equals(r.getOperation()))
                sensor.send(e);
            else if(OPERATION_GET_KEYS.equals(r.getOperation()) && sensor instanceof I2PBoteSensor)
                ((I2PBoteSensor)sensor).getKeys(e);
        } else {
            if (r.getOperation().equals(OPERATION_REPLY_CLEARNET)) {
                sensor = activeSensors.get(ClearnetSensor.class.getName());
                sensor.reply(e);
            } else {
                LOG.warning("Unable to determine sensor. Sending to Dead Letter queue.");
                deadLetter(e);
            }
        }
    }

    public void sendToBus(Envelope envelope) {
        LOG.info("Sending request to service bus from Sensors Service...");
        int maxAttempts = 30;
        int attempts = 0;
        while(!producer.send(envelope) && ++attempts <= maxAttempts) {
            synchronized (this) {
                try {
                    this.wait(100);
                } catch (InterruptedException e) {}
            }
        }
        if(attempts == maxAttempts) {
            // failed
            DLC.addErrorMessage("500",envelope);
        }
    }

    @Override
    public boolean start(Properties properties) {
        LOG.setLevel(Level.INFO);
        LOG.info("Starting...");
        try {
            config = Config.loadFromClasspath("sensors.config", properties, false);

            String registeredSensorsString = config.getProperty("1m5.sensors.registered");
            if(registeredSensorsString != null) {
                List<String> registered = Arrays.asList(registeredSensorsString.split(","));

                registeredSensors = new HashMap<>(registered.size());
                activeSensors = new HashMap<>(registered.size());

                if (registered.contains("bote")) {
                    registeredSensors.put(I2PBoteSensor.class.getName(), new I2PBoteSensor(this));
                    new AppThread(new Runnable() {
                        @Override
                        public void run() {
                            I2PBoteSensor i2PBoteSensor = (I2PBoteSensor) registeredSensors.get(I2PBoteSensor.class.getName());
                            i2PBoteSensor.start(config);
                            activeSensors.put(I2PBoteSensor.class.getName(), i2PBoteSensor);
                            LOG.info("I2PBoteSensor registered as active.");
                        }
                    }, SensorsService.class.getSimpleName()+":I2PBoteSensorStartThread").start();
                }

                if (registered.contains("i2p")) {
                    registeredSensors.put(I2PSensor.class.getName(), new I2PSensor());
                    new AppThread(new Runnable() {
                        @Override
                        public void run() {
                            I2PSensor i2PSensor = (I2PSensor) registeredSensors.get(I2PSensor.class.getName());
                            i2PSensor.start(config);
                            activeSensors.put(I2PSensor.class.getName(), i2PSensor);
                            LOG.info("I2PSensor registered as active.");
                        }
                    }, SensorsService.class.getSimpleName()+":I2PSensorStartThread").start();
                }

                if (registered.contains("tor")) {
                    registeredSensors.put(TorSensor.class.getName(), new TorSensor());
                    new AppThread(new Runnable() {
                        @Override
                        public void run() {
                            TorSensor torSensor = (TorSensor) registeredSensors.get(TorSensor.class.getName());
                            torSensor.start(config);
                            activeSensors.put(TorSensor.class.getName(), torSensor);
                            LOG.info("TorSensor registered as active.");
                        }
                    }, SensorsService.class.getSimpleName()+":TorSensorStartThread").start();
                }

                if (registered.contains("mesh")) {
                    registeredSensors.put(MeshSensor.class.getName(), new MeshSensor());
                    new AppThread(new Runnable() {
                        @Override
                        public void run() {
                            MeshSensor meshSensor = (MeshSensor) registeredSensors.get(MeshSensor.class.getName());
                            meshSensor.start(config);
                            activeSensors.put(MeshSensor.class.getName(), meshSensor);
                            LOG.info("MeshSensor registered as active.");
                        }
                    }, SensorsService.class.getSimpleName()+":MeshSensorStartThread").start();
                }

                if (registered.contains("clearnet")) {
                    registeredSensors.put(ClearnetSensor.class.getName(), new ClearnetSensor(this));
                    new AppThread(new Runnable() {
                        @Override
                        public void run() {
                            ClearnetSensor clearnetSensor = (ClearnetSensor) registeredSensors.get(ClearnetSensor.class.getName());
                            clearnetSensor.start(config);
                            activeSensors.put(ClearnetSensor.class.getName(), clearnetSensor);
                            LOG.info("ClearnetSensor registered as active.");
                        }
                    }, SensorsService.class.getSimpleName()+":ClearnetSensorStartThread").start();
                }
            }

            LOG.info("Started.");
        } catch (Exception e) {
            e.printStackTrace();
            LOG.warning("Failed to start.");
            return false;
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        if(registeredSensors.containsKey(ClearnetSensor.class.getName())) {
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    Sensor s = activeSensors.get(ClearnetSensor.class.getName());
                    if(s != null) s.gracefulShutdown();
                }
            }).start();
        }
        if(registeredSensors.containsKey(MeshSensor.class.getName())) {
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    Sensor s = activeSensors.get(MeshSensor.class.getName());
                    if(s != null) s.gracefulShutdown();
                }
            }).start();
        }
        if(registeredSensors.containsKey(TorSensor.class.getName())) {
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    Sensor s = activeSensors.get(TorSensor.class.getName());
                    if(s != null) s.gracefulShutdown();
                }
            }).start();
        }
        if(registeredSensors.containsKey(I2PSensor.class.getName())) {
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    Sensor s = activeSensors.get(I2PSensor.class.getName());
                    if(s != null) s.gracefulShutdown();
                }
            }).start();
        }
        if(registeredSensors.containsKey(I2PBoteSensor.class.getName())) {
            new AppThread(new Runnable() {
                @Override
                public void run() {
                    Sensor s = activeSensors.get(I2PBoteSensor.class.getName());
                    if(s != null) s.gracefulShutdown();
                }
            }).start();
        }
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        // TODO: add wait/checks to ensure each sensor shutdowns
        return shutdown();
    }
}
