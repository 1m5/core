package io.onemfive.core.orchestration.routes;

import io.onemfive.core.bus.Route;
import io.onemfive.data.Envelope;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class DAG extends RoutingSlip {

    public DAG(String service, String operation) {
        super(service, operation);
    }

    public DAG(Envelope envelope, String service, String operation) {
        super(envelope, service, operation);
    }

    protected void addRoute(Route route) throws Exception {
        // Ensure no Service is called twice (Acyclic)
        if(acyclic(route))
            routes.add(route);
    }

    private boolean acyclic(Route newRoute) {
        for(Route r : routes) {
            if(r.getService().equals(newRoute.getService())) return false;
        }
        return true;
    }
}
