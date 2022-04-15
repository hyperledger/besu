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
import org.hyperledger.besu.ethereum.core.LightBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByHashTaskV2 extends AbstractGetHeadersFromPeerTaskV2 {
  private static final Logger LOG = LoggerFactory.getLogger(GetHeadersFromPeerByHashTaskV2.class);

  private final Hash referenceHash;
  private final long minimumRequiredBlockNumber;

  @VisibleForTesting
  GetHeadersFromPeerByHashTaskV2(
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final int count,
      final int skip,
      final boolean reverse,
      final MetricsSystem metricsSystem) {
    super(ethContext, count, skip, reverse, metricsSystem);
    this.minimumRequiredBlockNumber = minimumRequiredBlockNumber;
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static AbstractGetHeadersFromPeerTaskV2 startingAtHash(
      final EthContext ethContext,
      final Hash firstHash,
      final long firstBlockNumber,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTaskV2(
        ethContext, firstHash, firstBlockNumber, segmentLength, 0, false, metricsSystem);
  }

  public static AbstractGetHeadersFromPeerTaskV2 endingAtHash(
      final EthContext ethContext,
      final Hash lastHash,
      final long lastBlockNumber,
      final int segmentLength,
      final MetricsSystem metricsSystem) {
    return new GetHeadersFromPeerByHashTaskV2(
        ethContext, lastHash, lastBlockNumber, segmentLength, 0, true, metricsSystem);
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
  protected boolean matchesFirstHeader(final LightBlockHeader firstHeader) {
    return firstHeader.getHash().equals(referenceHash);
  }
}
