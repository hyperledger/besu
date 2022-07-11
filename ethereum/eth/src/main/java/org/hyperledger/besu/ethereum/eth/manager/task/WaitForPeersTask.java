/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Waits for some number of peers to connect. */
public class WaitForPeersTask extends AbstractEthTask<Void> {
  private static final Logger LOG = LoggerFactory.getLogger(WaitForPeersTask.class);

  private final int targetPeerCount;
  private final EthContext ethContext;
  private volatile Long peerListenerId;

  private WaitForPeersTask(
      final EthContext ethContext, final int targetPeerCount, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.targetPeerCount = targetPeerCount;
    this.ethContext = ethContext;
  }

  public static WaitForPeersTask create(
      final EthContext ethContext, final int targetPeerCount, final MetricsSystem metricsSystem) {
    return new WaitForPeersTask(ethContext, targetPeerCount, metricsSystem);
  }

  @Override
  protected void executeTask() {
    final EthPeers ethPeers = ethContext.getEthPeers();
    if (ethPeers.peerCount() >= targetPeerCount) {
      // We already hit our target
      result.complete(null);
      return;
    }

    LOG.info(
        "Waiting for {} total peers to connect. {} peers currently connected.",
        targetPeerCount,
        ethPeers.peerCount());
    // Listen for peer connections and complete task when we hit our target
    peerListenerId =
        ethPeers.subscribeConnect(
            (peer) -> {
              final int peerCount = ethPeers.peerCount();
              if (peerCount >= targetPeerCount) {
                LOG.debug("Complete: {} peers connected.", targetPeerCount);
                // We hit our target
                result.complete(null);
              } else {
                LOG.debug(
                    "Waiting for {} total peers to connect. {} peers currently connected.",
                    targetPeerCount,
                    peerCount);
              }
            });
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    final Long listenerId = peerListenerId;
    if (listenerId != null) {
      ethContext.getEthPeers().unsubscribeConnect(peerListenerId);
    }
  }
}
