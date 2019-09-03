/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.peervalidation;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetHeadersFromPeerByNumberTask;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DaoForkPeerValidator implements PeerValidator {
  private static final Logger LOG = LogManager.getLogger();
  private static long DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER = 10L;

  private final EthContext ethContext;
  private final ProtocolSchedule<?> protocolSchedule;
  private final MetricsSystem metricsSystem;

  private final long daoBlockNumber;
  // Wait for peer's chainhead to advance some distance beyond daoBlockNumber before validating
  private final long chainHeightEstimationBuffer;

  public DaoForkPeerValidator(
      final EthContext ethContext,
      final ProtocolSchedule<?> protocolSchedule,
      final MetricsSystem metricsSystem,
      final long daoBlockNumber,
      final long chainHeightEstimationBuffer) {
    checkArgument(chainHeightEstimationBuffer >= 0);
    this.ethContext = ethContext;
    this.protocolSchedule = protocolSchedule;
    this.metricsSystem = metricsSystem;
    this.daoBlockNumber = daoBlockNumber;
    this.chainHeightEstimationBuffer = chainHeightEstimationBuffer;
  }

  public DaoForkPeerValidator(
      final EthContext ethContext,
      final ProtocolSchedule<?> protocolSchedule,
      final MetricsSystem metricsSystem,
      final long daoBlockNumber) {
    this(
        ethContext,
        protocolSchedule,
        metricsSystem,
        daoBlockNumber,
        DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER);
  }

  @Override
  public CompletableFuture<Boolean> validatePeer(final EthPeer ethPeer) {
    AbstractPeerTask<List<BlockHeader>> getHeaderTask =
        GetHeadersFromPeerByNumberTask.forSingleNumber(
                protocolSchedule, ethContext, daoBlockNumber, metricsSystem)
            .setTimeout(Duration.ofSeconds(20))
            .assignPeer(ethPeer);
    return getHeaderTask
        .run()
        .handle(
            (res, err) -> {
              if (err != null) {
                // Mark peer as invalid on error
                LOG.debug(
                    "Peer {} is invalid because DAO block ({}) is unavailable: {}",
                    ethPeer,
                    daoBlockNumber,
                    err.toString());
                return false;
              }
              List<BlockHeader> headers = res.getResult();
              if (headers.size() == 0) {
                // If no headers are returned, fail
                LOG.debug(
                    "Peer {} is invalid because DAO block ({}) is unavailable.",
                    ethPeer,
                    daoBlockNumber);
                return false;
              }
              BlockHeader header = headers.get(0);
              boolean validDaoBlock = MainnetBlockHeaderValidator.validateHeaderForDaoFork(header);
              if (!validDaoBlock) {
                LOG.debug(
                    "Peer {} is invalid because DAO block ({}) is invalid.",
                    ethPeer,
                    daoBlockNumber);
              }
              return validDaoBlock;
            });
  }

  @Override
  public boolean canBeValidated(final EthPeer ethPeer) {
    return ethPeer.chainState().getEstimatedHeight()
        >= (daoBlockNumber + chainHeightEstimationBuffer);
  }

  @Override
  public Duration nextValidationCheckTimeout(final EthPeer ethPeer) {
    if (!ethPeer.chainState().hasEstimatedHeight()) {
      return Duration.ofSeconds(30);
    }
    long distanceToDaoBlock = daoBlockNumber - ethPeer.chainState().getEstimatedHeight();
    if (distanceToDaoBlock < 100_000L) {
      return Duration.ofMinutes(1);
    }
    // If the peer is trailing behind, give it some time to catch up before trying again.
    return Duration.ofMinutes(10);
  }
}
