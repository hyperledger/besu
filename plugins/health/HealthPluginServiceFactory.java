package org.hyperledger.besu.plugins.health;

import org.hyperledger.besu.plugin.services.BesuPluginContextImpl;
import org.hyperledger.besu.plugin.services.PluginServiceFactory;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.health.LivenessCheckService;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckService;

public class HealthPluginServiceFactory implements PluginServiceFactory {
    @Override
    public void appendPluginServices(final BesuPluginContextImpl besuContext) {
        P2PService p2pService = besuContext.getService(P2PService.class).orElse(null);
        BesuEvents besuEvents = besuContext.getService(BesuEvents.class).orElse(null);
        if (p2pService != null && besuEvents != null) {
            besuContext.addService(LivenessCheckService.class, new LivenessCheckPluginService());
            besuContext.addService(ReadinessCheckService.class, new ReadinessCheckPluginService(p2pService, besuEvents));
        }
    }
}
