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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

abstract class TrieNodeDataRequest extends NodeDataRequest {

  TrieNodeDataRequest(final RequestType kind, final Hash hash) {
    super(kind, hash);
  }

  @Override
  public Stream<NodeDataRequest> getChildRequests() {
    if (getData() == null) {
      // If this node hasn't been downloaded yet, we can't return any child data
      return Stream.empty();
    }

    final List<Node<BytesValue>> nodes = TrieNodeDecoder.decodeNodes(getData());
    return nodes.stream()
        .flatMap(
            node -> {
              if (nodeIsHashReferencedDescendant(node)) {
                return Stream.of(createChildNodeDataRequest(Hash.wrap(node.getHash())));
              } else {
                return node.getValue()
                    .map(this::getRequestsFromTrieNodeValue)
                    .orElseGet(Stream::empty);
              }
            });
  }

  private boolean nodeIsHashReferencedDescendant(final Node<BytesValue> node) {
    return !Objects.equals(node.getHash(), getHash()) && node.isReferencedByHash();
  }

  protected abstract NodeDataRequest createChildNodeDataRequest(final Hash childHash);

  protected abstract Stream<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value);
}
