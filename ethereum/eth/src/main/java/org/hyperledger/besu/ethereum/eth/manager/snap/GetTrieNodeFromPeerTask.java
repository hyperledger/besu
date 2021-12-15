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
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodes;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodes;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetTrieNodeFromPeerTask extends AbstractPeerRequestTask<TrieNodes> {

  private static final Logger LOG = LogManager.getLogger();

  private final GetTrieNodes message;
  private final BlockHeader blockHeader;
  ;

  private GetTrieNodeFromPeerTask(
      final EthContext ethContext,
      final GetTrieNodes message,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, SnapV1.TRIE_NODES, metricsSystem);
    this.message = message;
    this.blockHeader = blockHeader;
  }

  public static GetTrieNodeFromPeerTask forTrieNodes(
      final EthContext ethContext,
      final GetTrieNodes message,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new GetTrieNodeFromPeerTask(ethContext, message, blockHeader, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.trace("Requesting trie nodes from {} : {} .", message.getData().toHexString(), peer);
          message.setOverrideStateRoot(Optional.of(blockHeader.getStateRoot()));
          return peer.getSnapTrieNode(message);
        },
        blockHeader.getNumber());
  }

  @Override
  protected Optional<TrieNodes> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.empty();
    }
    return Optional.of(TrieNodes.readFrom(message));
  }
}
