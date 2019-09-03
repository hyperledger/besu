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
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import static java.util.Collections.emptyMap;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.PendingPeerRequest;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.messages.NodeDataMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetNodeDataFromPeerTask extends AbstractPeerRequestTask<Map<Hash, BytesValue>> {

  private static final Logger LOG = LogManager.getLogger();

  private final Set<Hash> hashes;
  private final long pivotBlockNumber;

  private GetNodeDataFromPeerTask(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final long pivotBlockNumber,
      final MetricsSystem metricsSystem) {
    super(ethContext, EthPV63.GET_NODE_DATA, metricsSystem);
    this.hashes = new HashSet<>(hashes);
    this.pivotBlockNumber = pivotBlockNumber;
  }

  public static GetNodeDataFromPeerTask forHashes(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final long pivotBlockNumber,
      final MetricsSystem metricsSystem) {
    return new GetNodeDataFromPeerTask(ethContext, hashes, pivotBlockNumber, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.debug("Requesting {} node data entries from peer {}.", hashes.size(), peer);
          return peer.getNodeData(hashes);
        },
        pivotBlockNumber);
  }

  @Override
  protected Optional<Map<Hash, BytesValue>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.of(emptyMap());
    }
    final NodeDataMessage nodeDataMessage = NodeDataMessage.readFrom(message);
    final List<BytesValue> nodeData = nodeDataMessage.nodeData();
    if (nodeData.size() > hashes.size()) {
      // Can't be the response to our request
      return Optional.empty();
    }
    return mapNodeDataByHash(nodeData);
  }

  private Optional<Map<Hash, BytesValue>> mapNodeDataByHash(final List<BytesValue> nodeData) {
    final Map<Hash, BytesValue> nodeDataByHash = new HashMap<>();
    for (final BytesValue data : nodeData) {
      final Hash hash = Hash.hash(data);
      if (!hashes.contains(hash)) {
        return Optional.empty();
      }
      nodeDataByHash.put(hash, data);
    }
    return Optional.of(nodeDataByHash);
  }
}
