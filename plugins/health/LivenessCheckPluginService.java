package org.hyperledger.besu.plugins.health;

import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;

public class LivenessCheckPluginService implements HealthService.HealthCheck {
    @Override
    public boolean isHealthy(final HealthService.ParamSource params) {
        return true;
    }
}
