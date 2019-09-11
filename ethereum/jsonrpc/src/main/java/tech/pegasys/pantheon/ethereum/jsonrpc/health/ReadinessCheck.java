/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.health;

import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.jsonrpc.health.HealthService.HealthCheck;
import tech.pegasys.pantheon.ethereum.jsonrpc.health.HealthService.ParamSource;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.plugin.data.SyncStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReadinessCheck implements HealthCheck {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_MINIMUM_PEERS = 1;
  private static final int DEFAULT_MAX_BLOCKS_BEHIND = 2;
  private final P2PNetwork p2pNetwork;
  private final Synchronizer synchronizer;

  public ReadinessCheck(final P2PNetwork p2pNetwork, final Synchronizer synchronizer) {
    this.p2pNetwork = p2pNetwork;
    this.synchronizer = synchronizer;
  }

  @Override
  public boolean isHealthy(final ParamSource params) {
    LOG.debug("Invoking readiness check.");
    if (p2pNetwork.isP2pEnabled()) {
      final int peerCount = p2pNetwork.getPeerCount();
      if (!hasMinimumPeers(params, peerCount)) {
        return false;
      }
    }
    return synchronizer
        .getSyncStatus()
        .map(syncStatus -> isInSync(syncStatus, params))
        .orElse(true);
  }

  private boolean isInSync(final SyncStatus syncStatus, final ParamSource params) {
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

  private boolean hasMinimumPeers(final ParamSource params, final int peerCount) {
    final int minimumPeers;
    final String peersParam = params.getParam("minPeers");
    if (peersParam == null) {
      minimumPeers = DEFAULT_MINIMUM_PEERS;
    } else {
      try {
        minimumPeers = Integer.parseInt(peersParam);
      } catch (final NumberFormatException e) {
        LOG.debug("Invalid minPeers: {}. Reporting as not ready.", peersParam);
        return false;
      }
    }
    return peerCount >= minimumPeers;
  }
}
