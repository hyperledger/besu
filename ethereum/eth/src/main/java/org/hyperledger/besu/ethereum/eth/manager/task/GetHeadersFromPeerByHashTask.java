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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByHashTask extends AbstractGetHeadersFromPeerTask {
  private static final Logger LOG = LogManager.getLogger();

  private final Hash referenceHash;
  private final long minimumRequiredBlockNumber;

  @VisibleForTesting
  GetHeadersFromPeerByHashTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final int count,
      final int skip,
      final boolean reverse,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, ethContext, count, skip, reverse, metricsSystem);
    this.minimumRequiredBlockNumber = minimumRequiredBlockNumber;
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final long firstBlockNumber,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule,
        ethContext,
        firstHash,
        firstBlockNumber,
        segmentLength,
        0,
        false,
        metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final long firstBlockNumber,
      final int segmentLength,
      final int skip,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule,
        ethContext,
        firstHash,
        firstBlockNumber,
        segmentLength,
        skip,
        false,
        metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask endingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash lastHash,
      final long lastBlockNumber,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule,
        ethContext,
        lastHash,
        lastBlockNumber,
        segmentLength,
        0,
        true,
        metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask forSingleHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final long minimumRequiredBlockNumber,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, hash, minimumRequiredBlockNumber, 1, 0, false, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.debug("Requesting {} headers from peer {}.", count, peer);
          return peer.getHeadersByHash(referenceHash, count, skip, reverse);
        },
        minimumRequiredBlockNumber);
  }

  @Override
  protected boolean matchesFirstHeader(final BlockHeader firstHeader) {
    return firstHeader.getHash().equals(referenceHash);
  }
}
