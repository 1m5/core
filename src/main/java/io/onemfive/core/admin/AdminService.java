package io.onemfive.core.admin;

import io.onemfive.core.BaseService;
import io.onemfive.core.MessageProducer;
import io.onemfive.core.bus.ServiceBus;
import io.onemfive.core.bus.ServiceNotAccessibleException;
import io.onemfive.core.bus.ServiceNotSupportedException;
import io.onemfive.core.bus.ServiceRegisteredException;
import io.onemfive.data.util.DLC;
import io.onemfive.data.DocumentMessage;
import io.onemfive.data.Envelope;
import io.onemfive.data.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class AdminService extends BaseService {

    private static final Logger LOG = Logger.getLogger(AdminService.class.getName());

    public static final String OPERATION_REGISTER_SERVICES = "REGISTER_SERVICES";

    private ServiceBus serviceBus;

    public AdminService(MessageProducer producer) {
        super(producer);
        serviceBus = (ServiceBus)producer;
    }

    @Override
    public void handleDocument(Envelope e) {
        Route route = e.getRoute();
        switch(route.getOperation()) {
            case OPERATION_REGISTER_SERVICES:{registerServices(e);break;}
            default: deadLetter(e);
        }
    }

    private void registerServices(Envelope e){
        List<Class> servicesToRegister = (List<Class>)DLC.getEntity(e);
        Properties p = (Properties)DLC.getData(Properties.class, e);
        for(Class c : servicesToRegister) {
            try {
                serviceBus.register(c, p);
            } catch (ServiceNotAccessibleException e1) {
                DLC.addException(e1, e);
            } catch (ServiceNotSupportedException e1) {
                DLC.addException(e1, e);
            } catch (ServiceRegisteredException e1) {
                DLC.addException(e1, e);
            }
        }
    }

    @Override
    public boolean start(Properties properties) {
        LOG.info("Starting...");

        LOG.info("Started.");
        return true;
    }
}
