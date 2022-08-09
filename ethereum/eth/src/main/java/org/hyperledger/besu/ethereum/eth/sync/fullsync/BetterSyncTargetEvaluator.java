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
package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterSyncTargetEvaluator {
  private static final Logger LOG = LoggerFactory.getLogger(BetterSyncTargetEvaluator.class);
  private final ProtocolContext protocolContext;
  private final SynchronizerConfiguration config;
  private final EthPeers ethPeers;

  public BetterSyncTargetEvaluator(
      final ProtocolContext protocolContext,
      final SynchronizerConfiguration config,
      final EthPeers ethPeers) {
    this.protocolContext = protocolContext;
    this.config = config;
    this.ethPeers = ethPeers;
  }

  public boolean shouldSwitchSyncTarget(final EthPeer currentSyncTarget) {
    final Optional<EthPeer> maybeBestPeer = ethPeers.bestPeer();

    return maybeBestPeer
        .map(
            bestPeer -> {
              if (ethPeers.getBestChainComparator().compare(bestPeer, currentSyncTarget) <= 0) {
                debugLambda(
                    LOG,
                    "Our current target {} is better or equal to the best peer {}",
                    currentSyncTarget::toLogChainStateString,
                    bestPeer::toLogChainStateString);
                return false;
              }

              return maybeApplyStabilityFilter(currentSyncTarget, bestPeer);
            })
        .orElseGet(
            () -> {
              LOG.debug("No best peer found");
              return false;
            });
  }

  private boolean maybeApplyStabilityFilter(
      final EthPeer currentSyncTarget, final EthPeer bestPeer) {
    final ChainState currentPeerChainState = currentSyncTarget.chainState();
    final ChainHead localChainHead = protocolContext.getBlockchain().getChainHead();

    // Only if far from target, require some threshold to be exceeded before switching
    // targets to keep some stability when multiple peers are in range of each other
    final ChainState bestPeerChainState = bestPeer.chainState();

    final Difficulty tdGapFromChainHead =
        bestPeerChainState
            .getEstimatedTotalDifficulty()
            .subtract(localChainHead.getTotalDifficulty());
    final long heightGapFromChainHead =
        bestPeerChainState.getEstimatedHeight() - localChainHead.getHeight();

    traceLambda(
        LOG,
        "Distance from best peer {} is height {} total difficulty {}",
        bestPeer::toLogChainStateString,
        () -> heightGapFromChainHead,
        tdGapFromChainHead::toShortHexString);

    if (tdGapFromChainHead.compareTo(config.getDownloaderChangeTargetThresholdByTd()) > 0
        || heightGapFromChainHead > config.getDownloaderChangeTargetThresholdByHeight()) {
      debugLambda(LOG, "We are far from chain head so apply the stability filter before switching");

      final Difficulty tdDifference =
          bestPeerChainState
              .getEstimatedTotalDifficulty()
              .subtract(currentPeerChainState.getBestBlock().getTotalDifficulty());

      if (tdDifference.compareTo(config.getDownloaderChangeTargetThresholdByTd()) > 0) {
        debugLambda(
            LOG,
            "Switch to best peer {}, since total difficulty difference {} is greather than threshold {}",
            bestPeer::toLogChainStateString,
            tdDifference::toShortHexString,
            config.getDownloaderChangeTargetThresholdByTd()::toShortHexString);
        return true;
      }
      final long heightDifference =
          bestPeerChainState.getEstimatedHeight() - currentPeerChainState.getEstimatedHeight();
      if (heightDifference > config.getDownloaderChangeTargetThresholdByHeight()) {
        debugLambda(
            LOG,
            "Switch to best peer {}, since height difference {} is greather than threshold {}",
            bestPeer::toLogChainStateString,
            () -> heightDifference,
            config::getDownloaderChangeTargetThresholdByHeight);
        return true;
      } else {
        return false;
      }

    } else {
      debugLambda(
          LOG,
          "We are close to chain head so switch the sync target to best peer {}",
          bestPeer::toLogChainStateString);
      return true;
    }
  }
}
