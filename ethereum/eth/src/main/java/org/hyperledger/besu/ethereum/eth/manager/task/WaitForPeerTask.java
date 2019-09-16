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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Wait for a single new peer to connect. */
public class WaitForPeerTask extends AbstractEthTask<Void> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private volatile Long peerListenerId;

  private WaitForPeerTask(final EthContext ethContext, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.ethContext = ethContext;
  }

  public static WaitForPeerTask create(
      final EthContext ethContext, final MetricsSystem metricsSystem) {
    return new WaitForPeerTask(ethContext, metricsSystem);
  }

  @Override
  protected void executeTask() {
    final EthPeers ethPeers = ethContext.getEthPeers();
    LOG.debug(
        "Waiting for new peer connection. {} peers currently connected.", ethPeers.peerCount());
    // Listen for peer connections and complete task when we hit our target
    peerListenerId =
        ethPeers.subscribeConnect(
            (peer) -> {
              LOG.debug("Finished waiting for peer connection.");
              // We hit our target
              result.get().complete(null);
            });
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    final Long listenerId = peerListenerId;
    if (listenerId != null) {
      ethContext.getEthPeers().unsubscribeConnect(listenerId);
    }
  }
}
