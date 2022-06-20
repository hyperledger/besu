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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByHashTask extends AbstractGetHeadersFromPeerTask {
  private static final Logger LOG = LoggerFactory.getLogger(GetHeadersFromPeerByHashTask.class);

  private final Hash referenceHash;

  @VisibleForTesting
  GetHeadersFromPeerByHashTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final int count,
      final int skip,
      final boolean reverse,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, ethContext, count, skip, reverse, metricsSystem);
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, firstHash, segmentLength, 0, false, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final int segmentLength,
      final int skip,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, firstHash, segmentLength, skip, false, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask endingAtHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash lastHash,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, lastHash, segmentLength, 0, true, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTask forSingleHash(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash hash,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, hash, 1, 0, false, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.debug("Requesting {} headers from peer {}.", count, peer);
          return peer.getHeadersByHash(referenceHash, count, skip, reverse);
        });
  }

  @Override
  protected boolean matchesFirstHeader(final BlockHeader firstHeader) {
    return firstHeader.getHash().equals(referenceHash);
  }
}
