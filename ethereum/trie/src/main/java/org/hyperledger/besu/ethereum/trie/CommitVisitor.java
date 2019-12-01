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
package org.hyperledger.besu.ethereum.trie;

import org.apache.tuweni.bytes.Bytes;

class CommitVisitor<V> implements NodeVisitor<V> {

  private final NodeUpdater nodeUpdater;

  public CommitVisitor(final NodeUpdater nodeUpdater) {
    this.nodeUpdater = nodeUpdater;
  }

  @Override
  public void visit(final ExtensionNode<V> extensionNode) {
    if (!extensionNode.isDirty()) {
      return;
    }

    final Node<V> child = extensionNode.getChild();
    if (child.isDirty()) {
      child.accept(this);
    }

    maybeStoreNode(extensionNode);
  }

  @Override
  public void visit(final BranchNode<V> branchNode) {
    if (!branchNode.isDirty()) {
      return;
    }

    for (byte i = 0; i < BranchNode.RADIX; ++i) {
      final Node<V> child = branchNode.child(i);
      if (child.isDirty()) {
        child.accept(this);
      }
    }

    maybeStoreNode(branchNode);
  }

  @Override
  public void visit(final LeafNode<V> leafNode) {
    if (!leafNode.isDirty()) {
      return;
    }

    maybeStoreNode(leafNode);
  }

  @Override
  public void visit(final NullNode<V> nullNode) {}

  private void maybeStoreNode(final Node<V> node) {
    final Bytes nodeRLP = node.getRlp();
    if (nodeRLP.size() >= 32) {
      this.nodeUpdater.store(node.getHash(), nodeRLP);
    }
  }
}
