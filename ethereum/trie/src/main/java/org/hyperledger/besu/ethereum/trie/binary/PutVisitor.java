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
package org.hyperledger.besu.ethereum.trie.binary;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.BranchNode;
import org.hyperledger.besu.ethereum.trie.ExtensionNode;
import org.hyperledger.besu.ethereum.trie.LeafNode;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;

public class PutVisitor<V> implements PathNodeVisitor<V> {
  private final NodeFactory<V> nodeFactory;
  private final V value;

  public PutVisitor(final NodeFactory<V> nodeFactory, final V value) {
    this.nodeFactory = nodeFactory;
    this.value = value;
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    final byte childIndex = path.get(0);
    final Node<V> updatedChild = branchNode.child(childIndex).accept(this, path.slice(1));
    return branchNode.replaceChild(childIndex, updatedChild);
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    final Bytes leafPath = leafNode.getPath();
    final int commonPathLength = leafPath.commonPrefixLength(path);

    // Check if the current leaf node should be replaced
    if (commonPathLength == leafPath.size() && commonPathLength == path.size()) {
      return nodeFactory.createLeaf(leafPath, value);
    }

    assert commonPathLength < leafPath.size() && commonPathLength < path.size()
            : "Should not have consumed non-matching terminator";

    // The current leaf path must be split to accommodate the new value.
    final byte newLeafIndex = path.get(0);
    final Bytes newLeafPath = path.slice( 1);

    final byte updatedLeafIndex = leafPath.get(0);
    final Node<V> updatedLeaf = leafNode.replacePath(leafPath.slice(1));

    final Node<V> leaf = nodeFactory.createLeaf(newLeafPath, value);
    return nodeFactory.createBranch(updatedLeafIndex, updatedLeaf, newLeafIndex, leaf);
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    throw new MerkleTrieException("extension node not allowed in the sparse merkle trie");
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    return nodeFactory.createLeaf(path, value);
  }
}
