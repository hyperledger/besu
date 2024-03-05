/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.trie.RangeManager.createPath;
import static org.hyperledger.besu.ethereum.trie.RangeManager.isInRange;

import org.hyperledger.besu.ethereum.trie.patricia.BranchNode;
import org.hyperledger.besu.ethereum.trie.patricia.ExtensionNode;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class SnapCommitVisitor<V> extends CommitVisitor<V> implements LocationNodeVisitor<V> {

  private final Bytes startKeyPath;
  private final Bytes endKeyPath;

  public SnapCommitVisitor(
      final NodeUpdater nodeUpdater, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    super(nodeUpdater);
    this.startKeyPath = createPath(startKeyHash);
    this.endKeyPath = createPath(endKeyHash);
  }

  @Override
  public void visit(final Bytes location, final ExtensionNode<V> extensionNode) {
    if (!extensionNode.isDirty()) {
      return;
    }

    final Node<V> child = extensionNode.getChild();
    if (child.isDirty()) {
      child.accept(Bytes.concatenate(location, extensionNode.getPath()), this);
    }
    if (child.isHealNeeded()
        || !isInRange(
            Bytes.concatenate(location, extensionNode.getPath()), startKeyPath, endKeyPath)) {
      extensionNode.markHealNeeded(); // not save an incomplete node
    }

    maybeStoreNode(location, extensionNode);
  }

  @Override
  public void visit(final Bytes location, final BranchNode<V> branchNode) {
    if (!branchNode.isDirty()) {
      return;
    }

    for (int i = 0; i < branchNode.maxChild(); ++i) {
      Bytes index = Bytes.of(i);
      final Node<V> child = branchNode.child((byte) i);
      if (child.isDirty()) {
        child.accept(Bytes.concatenate(location, index), this);
      }
      if (child.isHealNeeded()
          || !isInRange(Bytes.concatenate(location, index), startKeyPath, endKeyPath)) {
        branchNode.markHealNeeded(); // not save an incomplete node
      }
    }

    maybeStoreNode(location, branchNode);
  }
}
