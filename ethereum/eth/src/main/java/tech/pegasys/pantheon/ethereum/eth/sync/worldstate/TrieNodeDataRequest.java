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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.trie.Node;
import tech.pegasys.pantheon.ethereum.trie.TrieNodeDecoder;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Objects;

abstract class TrieNodeDataRequest extends NodeDataRequest {

  TrieNodeDataRequest(final RequestType kind, final Hash hash) {
    super(kind, hash);
  }

  @Override
  public List<NodeDataRequest> getChildRequests() {
    if (getData() == null) {
      // If this node hasn't been downloaded yet, we can't return any child data
      return Collections.emptyList();
    }

    List<Node<BytesValue>> nodes = TrieNodeDecoder.decodeNodes(getData());
    // Collect hash-referenced child nodes to be requested
    List<NodeDataRequest> requests =
        nodes.stream()
            .filter(this::nodeIsHashReferencedDescendant)
            .map(Node::getHash)
            .map(Hash::wrap)
            .map(this::createChildNodeDataRequest)
            .collect(Collectors.toList());

    // Collect any requests embedded in leaf values
    nodes.stream()
        .filter(this::canReadNodeValue)
        .map(Node::getValue)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(this::getRequestsFromTrieNodeValue)
        .forEach(requests::addAll);

    return requests;
  }

  private boolean nodeIsHashReferencedDescendant(final Node<BytesValue> node) {
    return !Objects.equal(node.getHash(), getHash()) && node.isReferencedByHash();
  }

  private boolean canReadNodeValue(final Node<BytesValue> node) {
    return !nodeIsHashReferencedDescendant(node);
  }

  protected abstract NodeDataRequest createChildNodeDataRequest(final Hash childHash);

  protected abstract List<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value);
}
