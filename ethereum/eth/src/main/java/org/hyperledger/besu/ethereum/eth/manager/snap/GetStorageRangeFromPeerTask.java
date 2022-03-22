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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerRequestTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetStorageRangeFromPeerTask
    extends AbstractPeerRequestTask<StorageRangeMessage.SlotRangeData> {

  private static final Logger LOG = LoggerFactory.getLogger(GetStorageRangeFromPeerTask.class);

  private final List<Bytes32> accountHashes;
  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;
  private final BlockHeader blockHeader;

  private GetStorageRangeFromPeerTask(
      final EthContext ethContext,
      final List<Bytes32> accountHashes,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, SnapV1.STORAGE_RANGE, metricsSystem);
    this.accountHashes = accountHashes;
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.blockHeader = blockHeader;
  }

  public static GetStorageRangeFromPeerTask forStorageRange(
      final EthContext ethContext,
      final List<Bytes32> accountHashes,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new GetStorageRangeFromPeerTask(
        ethContext, accountHashes, startKeyHash, endKeyHash, blockHeader, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.trace(
              "Requesting storage range [{} ,{}] for {} accounts from peer {} .",
              startKeyHash,
              endKeyHash,
              accountHashes.size(),
              peer);
          return peer.getSnapStorageRange(
              blockHeader.getStateRoot(), accountHashes, startKeyHash, endKeyHash);
        },
        blockHeader.getNumber());
  }

  @Override
  protected Optional<StorageRangeMessage.SlotRangeData> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {

    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.empty();
    }

    return Optional.of(StorageRangeMessage.readFrom(message).slotsData(true));
  }
}
