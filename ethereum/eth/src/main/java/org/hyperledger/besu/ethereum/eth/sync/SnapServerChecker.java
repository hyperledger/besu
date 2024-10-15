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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.snap.GetAccountRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapServerChecker {

  private static final Logger LOG = LoggerFactory.getLogger(SnapServerChecker.class);

  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public SnapServerChecker(final EthContext ethContext, final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public static void createAndSetSnapServerChecker(
      final EthContext ethContext, final MetricsSystem metricsSystem) {
    final SnapServerChecker checker = new SnapServerChecker(ethContext, metricsSystem);
    ethContext.getEthPeers().setSnapServerChecker(checker);
  }

  public CompletableFuture<Boolean> check(final EthPeer peer, final BlockHeader peersHeadHeader) {
    LOG.atTrace()
        .setMessage("Checking whether peer {} is a snap server ...")
        .addArgument(peer::getLoggableId)
        .log();
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<AccountRangeMessage.AccountRangeData>>
        snapServerCheckCompletableFuture = getAccountRangeFromPeer(peer, peersHeadHeader);
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    snapServerCheckCompletableFuture.whenComplete(
        (peerResult, error) -> {
          if (peerResult != null) {
            if (!peerResult.getResult().accounts().isEmpty()
                || !peerResult.getResult().proofs().isEmpty()) {
              LOG.atTrace()
                  .setMessage("Peer {} is a snap server.")
                  .addArgument(peer::getLoggableId)
                  .log();
              future.complete(true);
            } else {
              LOG.atTrace()
                  .setMessage("Peer {} is not a snap server.")
                  .addArgument(peer::getLoggableId)
                  .log();
              future.complete(false);
            }
          }
        });
    return future;
  }

  public CompletableFuture<AbstractPeerTask.PeerTaskResult<AccountRangeMessage.AccountRangeData>>
      getAccountRangeFromPeer(final EthPeer peer, final BlockHeader header) {
    return GetAccountRangeFromPeerTask.forAccountRange(
            ethContext, Hash.ZERO, Hash.ZERO, header, metricsSystem)
        .assignPeer(peer)
        .run();
  }
}
