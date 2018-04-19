package io.onemfive.core.bus;

import io.onemfive.core.BaseService;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.util.AppThread;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
class WorkerThreadPool extends AppThread {

    public enum Status {Starting, Running, Stopping, Stopped}

    private Status status = Status.Stopped;

    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    private final ClientAppManager clientAppManager;
    private Map<String,BaseService> services;
    private MessageChannel channel;
    private ExecutorService pool;
    private int poolSize = NUMBER_OF_CORES * 2; // default
    private int maxPoolSize = NUMBER_OF_CORES * 2; // default
    private Properties properties;
    private AtomicBoolean spin = new AtomicBoolean(true);

    WorkerThreadPool(ClientAppManager clientAppManager, Map<String, BaseService> services, MessageChannel channel, int poolSize, int maxPoolSize, Properties properties) {
        this.clientAppManager = clientAppManager;
        this.services = services;
        this.channel = channel;
        this.poolSize = poolSize;
        this.maxPoolSize = maxPoolSize;
        this.properties = properties;
    }

    @Override
    public void run() {
        startPool();
        status = Status.Stopped;
    }

    private boolean startPool() {
        int index = 0;
        status = Status.Starting;
        pool = Executors.newFixedThreadPool(maxPoolSize);
        status = Status.Running;
        while(spin.get()) {
            synchronized (this){
                try {
                    System.out.println("*");
                    if(channel.getQueue().size() > 0) {
                        String threadName = "FactoryThread-"+(++index);
                        System.out.println("Pool: Queue > 0 : Launching "+threadName);
                        pool.execute(new WorkerThread(threadName,channel, clientAppManager, services));
                    } else {
                        this.wait(1000); // wait
                    }
                } catch (InterruptedException e) {

                }
            }
        }
        return true;
    }

    boolean pause() {
        return false;
    }

    boolean unpause() {
        return false;
    }

    boolean restart() {
        return false;
    }

    boolean shutdown() {
        status = Status.Stopping;
        spin.set(false);
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                // pool didn't terminate after the first try
                pool.shutdownNow();
            }


            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                // pool didn't terminate after the second try
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        status = Status.Stopped;
        return true;
    }

    public Status getStatus() {
        return status;
    }
}
