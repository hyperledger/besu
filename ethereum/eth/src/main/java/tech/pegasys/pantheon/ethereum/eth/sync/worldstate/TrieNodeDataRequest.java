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

import java.util.List;
import java.util.stream.Stream;

abstract class TrieNodeDataRequest extends NodeDataRequest {

  private static final TrieNodeDecoder nodeDecoder = TrieNodeDecoder.create();

  TrieNodeDataRequest(final RequestType kind, final Hash hash) {
    super(kind, hash);
  }

  @Override
  public Stream<NodeDataRequest> getChildRequests() {
    if (getData() == null) {
      // If this node hasn't been downloaded yet, we can't return any child data
      return Stream.empty();
    }

    final Node<BytesValue> node = nodeDecoder.decode(getData());
    return getRequestsFromLoadedTrieNode(node);
  }

  private Stream<NodeDataRequest> getRequestsFromLoadedTrieNode(final Node<BytesValue> trieNode) {
    // Process this node's children
    final Stream<NodeDataRequest> childRequests =
        trieNode
            .getChildren()
            .map(List::stream)
            .map(s -> s.flatMap(this::getRequestsFromChildTrieNode))
            .orElse(Stream.of());

    // Process value at this node, if present
    return trieNode
        .getValue()
        .map(v -> Stream.concat(childRequests, (getRequestsFromTrieNodeValue(v).stream())))
        .orElse(childRequests);
  }

  private Stream<NodeDataRequest> getRequestsFromChildTrieNode(final Node<BytesValue> trieNode) {
    if (trieNode.isReferencedByHash()) {
      // If child nodes are reference by hash, we need to download them
      NodeDataRequest req = createChildNodeDataRequest(Hash.wrap(trieNode.getHash()));
      return Stream.of(req);
    }
    // Otherwise if the child's value has been inlined we can go ahead and process it
    return getRequestsFromLoadedTrieNode(trieNode);
  }

  protected abstract NodeDataRequest createChildNodeDataRequest(final Hash childHash);

  protected abstract List<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value);
}
