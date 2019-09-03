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
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.PendingPeerRequest;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByNumberTask extends AbstractGetHeadersFromPeerTask {
  private static final Logger LOG = LogManager.getLogger();

  private final long blockNumber;

  @VisibleForTesting
  GetHeadersFromPeerByNumberTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long blockNumber,
      final int count,
      final int skip,
      final boolean reverse,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, ethContext, count, skip, reverse, metricsSystem);
    this.blockNumber = blockNumber;
  }

  public static AbstractGetHeadersFromPeerTask startingAtNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long firstBlockNumber,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, firstBlockNumber, segmentLength, 0, false, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask endingAtNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long lastlockNumber,
      final int segmentLength,
      final int skip,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, lastlockNumber, segmentLength, skip, true, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask forSingleNumber(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByNumberTask(
        protocolSchedule, ethContext, blockNumber, 1, 0, false, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.debug("Requesting {} headers from peer {}.", count, peer);
          return peer.getHeadersByNumber(blockNumber, count, skip, reverse);
        },
        blockNumber);
  }

  @Override
  protected boolean matchesFirstHeader(final BlockHeader firstHeader) {
    return firstHeader.getNumber() == blockNumber;
  }
}
