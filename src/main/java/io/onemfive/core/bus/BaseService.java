package io.onemfive.core.bus;

import io.onemfive.data.CommandMessage;
import io.onemfive.data.DocumentMessage;
import io.onemfive.data.Envelope;
import io.onemfive.data.EventMessage;

import java.util.Properties;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public abstract class BaseService implements MessageConsumer, Service, LifeCycle {

    private MessageProducer producer;

    public BaseService(MessageProducer producer) {
        this.producer = producer;
    }

    @Override
    public boolean receive(Envelope envelope) {
        System.out.println(BaseService.class.getSimpleName()+": Envelope received by service. Handling...");
        if(envelope.getMessage() instanceof DocumentMessage)
            handleDocument(envelope);
        else if(envelope.getMessage() instanceof EventMessage)
            handleEvent(envelope);
        else if(envelope.getMessage() instanceof CommandMessage)
            runCommand(envelope);
        return true;
    }

    protected final void deadLetter(Envelope envelope) {
        System.out.println("Unable to find operation ("+envelope.getHeader(Envelope.OPERATION)+") in service ("+envelope.getHeader(Envelope.SERVICE)+").");
    }

    @Override
    public void handleDocument(Envelope envelope) {System.out.println(this.getClass().getName()+" has not implemented handleDocument().");}

    @Override
    public void handleEvent(Envelope envelope) {System.out.println(this.getClass().getName()+" has not implemented handleEvent().");}

    /**
     * Supports synchronous high-priority calls from ServiceBus and asynchronous low-priority calls from receive()
     * @param envelope
     */
    final void runCommand(Envelope envelope) {
        System.out.println("Running command by service...");
        CommandMessage m = (CommandMessage)envelope.getMessage();
        switch(m.getCommand()) {
            case Shutdown: {shutdown();break;}
            case Restart: {restart();break;}
            case Start: {
                Properties p = (Properties)envelope.getHeader(Properties.class.getName());
                start(p);
            }
        }
    }

    protected final void reply(Envelope envelope) {
        System.out.println(BaseService.class.getSimpleName()+": Sending reply to service bus...");
        int maxAttempts = 30;
        int attempts = 0;
        envelope.setHeader(Envelope.REPLY,true);
        while(!producer.send(envelope) && ++attempts <= maxAttempts) {
            synchronized (this) {
                try {
                    this.wait(100);
                } catch (InterruptedException e) {}
            }
        }
    }

    @Override
    public boolean start(Properties properties) {
        return false;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean unpause() {
        return false;
    }

    @Override
    public boolean restart() {
        return false;
    }

    @Override
    public boolean shutdown() {
        return false;
    }

    @Override
    public boolean gracefulShutdown() {
        return false;
    }
}
