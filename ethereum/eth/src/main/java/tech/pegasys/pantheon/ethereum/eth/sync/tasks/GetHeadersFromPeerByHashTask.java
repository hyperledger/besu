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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retrieves a sequence of headers from a peer. */
public class GetHeadersFromPeerByHashTask extends AbstractGetHeadersFromPeerTask {
  private static final Logger LOG = LogManager.getLogger();

  private final Hash referenceHash;

  @VisibleForTesting
  GetHeadersFromPeerByHashTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash referenceHash,
      final long minimumRequiredBlockNumber,
      final int count,
      final int skip,
      final boolean reverse) {
    super(protocolSchedule, ethContext, minimumRequiredBlockNumber, count, skip, reverse);
    checkNotNull(referenceHash);
    this.referenceHash = referenceHash;
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final long firstBlockNumber,
      final int segmentLength) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, firstHash, firstBlockNumber, segmentLength, 0, false);
  }

  public static AbstractGetHeadersFromPeerTask startingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash firstHash,
      final long firstBlockNumber,
      final int segmentLength,
      final int skip) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, firstHash, firstBlockNumber, segmentLength, skip, false);
  }

  public static AbstractGetHeadersFromPeerTask endingAtHash(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Hash lastHash,
      final long lastBlockNumber,
      final int segmentLength) {
    return new GetHeadersFromPeerByHashTask(
        protocolSchedule, ethContext, lastHash, lastBlockNumber, segmentLength, 0, true);
  }

  public static AbstractGetHeadersFromPeerTask forSingleHash(
      final ProtocolSchedule<?> protocolSchedule, final EthContext ethContext, final Hash hash) {
    return new GetHeadersFromPeerByHashTask(protocolSchedule, ethContext, hash, 0, 1, 0, false);
  }

  @Override
  protected ResponseStream sendRequest(final EthPeer peer) throws PeerNotConnected {
    LOG.debug("Requesting {} headers from peer {}.", count, peer);
    return peer.getHeadersByHash(referenceHash, count, skip, reverse);
  }

  @Override
  protected boolean matchesFirstHeader(final BlockHeader firstHeader) {
    return firstHeader.getHash().equals(referenceHash);
  }
}
