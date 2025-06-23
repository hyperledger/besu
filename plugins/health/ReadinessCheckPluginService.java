package org.hyperledger.besu.plugins.health;

import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.data.SyncStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadinessCheckPluginService implements HealthService.HealthCheck {
    private static final Logger LOG = LoggerFactory.getLogger(ReadinessCheckPluginService.class);
    private static final int DEFAULT_MINIMUM_PEERS = 1;
    private static final int DEFAULT_MAX_BLOCKS_BEHIND = 2;

    private final P2PService p2pService;
    private final BesuEvents besuEvents;
    private volatile SyncStatus latestSyncStatus = null;

    public ReadinessCheckPluginService(final P2PService p2pService, final BesuEvents besuEvents) {
        this.p2pService = p2pService;
        this.besuEvents = besuEvents;
        this.besuEvents.addSyncStatusListener(syncStatusOpt -> {
            this.latestSyncStatus = syncStatusOpt.orElse(null);
        });
    }

    @Override
    public boolean isHealthy(final HealthService.ParamSource params) {
        LOG.debug("Invoking readiness check (plugin).");
        if (p2pService.isP2pEnabled()) {
            int peerCount = p2pService.getPeerCount();
            if (!hasMinimumPeers(params, peerCount)) {
                return false;
            }
        }
        return isInSync(latestSyncStatus, params);
    }

    private boolean hasMinimumPeers(final HealthService.ParamSource params, final int peerCount) {
        int minimumPeers = DEFAULT_MINIMUM_PEERS;
        if (params != null) {
            String peersParam = params.getParam("minPeers");
            if (peersParam != null) {
                try {
                    minimumPeers = Integer.parseInt(peersParam);
                } catch (NumberFormatException e) {
                    LOG.debug("Invalid minPeers: {}. Reporting as not ready.", peersParam);
                    return false;
                }
            }
        }
        return peerCount >= minimumPeers;
    }

    private boolean isInSync(final SyncStatus syncStatus, final HealthService.ParamSource params) {
        final String maxBlocksBehindParam = params.getParam("maxBlocksBehind");
        final long maxBlocksBehind;

        if (maxBlocksBehindParam == null) {
            maxBlocksBehind = DEFAULT_MAX_BLOCKS_BEHIND;
        } else {
            try {
                maxBlocksBehind = Long.parseLong(maxBlocksBehindParam);
            } catch (final NumberFormatException e) {
                LOG.debug("Invalid maxBlocksBehind: {}. Reporting as not ready.", maxBlocksBehindParam);
                return false;
            }
        }
        return syncStatus.getHighestBlock() - syncStatus.getCurrentBlock() <= maxBlocksBehind;
    }
}
