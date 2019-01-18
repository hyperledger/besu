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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerRequestTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.messages.NodeDataMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetNodeDataFromPeerTask extends AbstractPeerRequestTask<List<BytesValue>> {

  private static final Logger LOG = LogManager.getLogger();

  private final Set<Hash> hashes;

  private GetNodeDataFromPeerTask(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(ethContext, EthPV63.GET_NODE_DATA, ethTasksTimer);
    this.hashes = new HashSet<>(hashes);
  }

  public static GetNodeDataFromPeerTask forHashes(
      final EthContext ethContext,
      final Collection<Hash> hashes,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    return new GetNodeDataFromPeerTask(ethContext, hashes, ethTasksTimer);
  }

  @Override
  protected ResponseStream sendRequest(final EthPeer peer) throws PeerNotConnected {
    LOG.debug("Requesting {} node data entries from peer {}.", hashes.size(), peer);
    return peer.getNodeData(hashes);
  }

  @Override
  protected Optional<List<BytesValue>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.of(emptyList());
    }
    final NodeDataMessage nodeDataMessage = NodeDataMessage.readFrom(message);
    final List<BytesValue> nodeData = nodeDataMessage.nodeData();
    if (nodeData.isEmpty()) {
      return Optional.empty();
    } else if (nodeData.size() > hashes.size()) {
      // Can't be the response to our request
      return Optional.empty();
    }

    if (nodeData.stream().anyMatch(data -> !hashes.contains(Hash.hash(data)))) {
      // Message contains unrequested data, must not be the response to our request.
      return Optional.empty();
    }
    return Optional.of(nodeData);
  }
}
