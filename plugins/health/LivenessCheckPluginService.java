package org.hyperledger.besu.plugins.health;

import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;

import org.hyperledger.besu.plugin.services.BesuService;

public class LivenessCheckPluginService implements org.hyperledger.besu.plugin.services.health.LivenessCheckService {
    @Override
    public boolean isHealthy(final HealthService.ParamSource params) {
        return true;
    }
}
