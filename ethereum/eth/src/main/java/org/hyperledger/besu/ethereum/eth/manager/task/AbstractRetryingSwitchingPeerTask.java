/*
 * Copyright contributors to Hyperledger Besu
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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRetryingSwitchingPeerTask<T> extends AbstractRetryingPeerTask<T> {

  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractRetryingSwitchingPeerTask.class);

  private final Set<EthPeer> triedPeers = new HashSet<>();
  private final Set<EthPeer> failedPeers = new HashSet<>();

  protected AbstractRetryingSwitchingPeerTask(
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final Predicate<T> isEmptyResponse,
      final int maxRetries) {
    super(ethContext, maxRetries, isEmptyResponse, metricsSystem);
  }

  @Override
  public void assignPeer(final EthPeer peer) {
    super.assignPeer(peer);
    triedPeers.add(peer);
  }

  protected abstract CompletableFuture<T> executeTaskOnCurrentPeer(final EthPeer peer);

  @Override
  protected CompletableFuture<T> executePeerTask(final Optional<EthPeer> assignedPeer) {

    final Optional<EthPeer> maybePeer =
        assignedPeer
            .filter(u -> getRetryCount() == 1) // first try with the assigned peer if present
            .map(Optional::of)
            .orElseGet(this::selectNextPeer); // otherwise select a new one from the pool

    if (maybePeer.isEmpty()) {
      traceLambda(
          LOG,
          "No peer found to try to execute task at attempt {}, tried peers {}",
          this::getRetryCount,
          triedPeers::toString);
      final var ex = new NoAvailablePeersException();
      return CompletableFuture.failedFuture(ex);
    }

    final EthPeer peerToUse = maybePeer.get();
    assignPeer(peerToUse);

    traceLambda(
        LOG,
        "Trying to execute task on peer {}, attempt {}",
        this::getAssignedPeer,
        this::getRetryCount);

    return executeTaskOnCurrentPeer(peerToUse)
        .thenApply(
            peerResult -> {
              traceLambda(
                  LOG,
                  "Got result {} from peer {}, attempt {}",
                  peerResult::toString,
                  peerToUse::toString,
                  this::getRetryCount);
              result.complete(peerResult);
              return peerResult;
            });
  }

  @Override
  protected void handleTaskError(final Throwable error) {
    if (isPeerFailure(error)) {
      getAssignedPeer().ifPresent(peer -> failedPeers.add(peer));
    }
    super.handleTaskError(error);
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return error instanceof TimeoutException || isPeerFailure(error);
  }

  private Optional<EthPeer> selectNextPeer() {
    final Optional<EthPeer> maybeNextPeer = remainingPeersToTry().findFirst();

    if (maybeNextPeer.isEmpty()) {
      // tried all the peers, restart from the best one but excluding the failed ones
      refreshPeers();
      triedPeers.retainAll(failedPeers);
      return remainingPeersToTry().findFirst();
    }

    return maybeNextPeer;
  }

  private Stream<EthPeer> remainingPeersToTry() {
    return getEthContext()
        .getEthPeers()
        .streamBestPeers()
        .filter(peer -> !triedPeers.contains(peer));
  }

  private void refreshPeers() {
    final EthPeers peers = getEthContext().getEthPeers();
    // If we are at max connections, then refresh peers disconnecting one of the failed peers,
    // or the least useful
    if (peers.peerCount() >= peers.getMaxPeers()) {
      failedPeers.stream()
          .filter(peer -> !peer.isDisconnected())
          .findAny()
          .or(() -> peers.streamAvailablePeers().sorted(peers.getBestChainComparator()).findFirst())
          .ifPresent(
              peer -> {
                debugLambda(LOG, "Refresh peers disconnecting peer {}", peer::toString);
                peer.disconnect(DisconnectReason.USELESS_PEER);
              });
    }
  }
}
