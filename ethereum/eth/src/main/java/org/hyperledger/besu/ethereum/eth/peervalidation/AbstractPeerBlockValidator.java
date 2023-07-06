/*
 *
 *  * Copyright 2019 ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.eth.peervalidation;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByNumberTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractPeerBlockValidator implements PeerValidator {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPeerBlockValidator.class);
  static long DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER = 10L;

  private final ProtocolSchedule protocolSchedule;
  private final MetricsSystem metricsSystem;

  final long blockNumber;
  // Wait for peer's chainhead to advance some distance beyond blockNumber before validating
  private final long chainHeightEstimationBuffer;

  AbstractPeerBlockValidator(
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final long blockNumber,
      final long chainHeightEstimationBuffer) {
    checkArgument(chainHeightEstimationBuffer >= 0);
    this.protocolSchedule = protocolSchedule;
    this.metricsSystem = metricsSystem;
    this.blockNumber = blockNumber;
    this.chainHeightEstimationBuffer = chainHeightEstimationBuffer;
  }

  protected AbstractPeerBlockValidator(
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final long blockNumber) {
    this(protocolSchedule, metricsSystem, blockNumber, DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER);
  }

  @Override
  public CompletableFuture<Boolean> validatePeer(
      final EthContext ethContext, final EthPeer ethPeer) {
    final AbstractPeerTask<List<BlockHeader>> getHeaderTask =
        GetHeadersFromPeerByNumberTask.forSingleNumber(
                protocolSchedule, ethContext, blockNumber, metricsSystem)
            .setTimeout(Duration.ofSeconds(20))
            .assignPeer(ethPeer);
    return getHeaderTask
        .run()
        .handle(
            (res, err) -> {
              if (err != null) {
                // Mark peer as invalid on error
                LOG.debug(
                    "Peer {} is invalid because required block ({}) is unavailable: {}",
                    ethPeer,
                    blockNumber,
                    err.toString());
                return false;
              }
              final List<BlockHeader> headers = res.getResult();
              if (headers.size() == 0) {
                // If no headers are returned, fail
                LOG.debug(
                    "Peer {} is invalid because required block ({}) is unavailable.",
                    ethPeer,
                    blockNumber);
                return false;
              }
              final BlockHeader header = headers.get(0);
              return validateBlockHeader(ethPeer, header);
            });
  }

  abstract boolean validateBlockHeader(EthPeer ethPeer, BlockHeader header);

  @Override
  public boolean canBeValidated(final EthPeer ethPeer) {
    return ethPeer.chainState().getEstimatedHeight() >= (blockNumber + chainHeightEstimationBuffer);
  }

  @Override
  public Duration nextValidationCheckTimeout(final EthPeer ethPeer) {
    if (!ethPeer.chainState().hasEstimatedHeight()) {
      return Duration.ofSeconds(30);
    }
    final long distanceToBlock = blockNumber - ethPeer.chainState().getEstimatedHeight();
    if (distanceToBlock < 100_000L) {
      return Duration.ofMinutes(1);
    }
    // If the peer is trailing behind, give it some time to catch up before trying again.
    return Duration.ofMinutes(10);
  }
}
