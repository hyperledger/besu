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

import java.util.HashSet;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class CollectBranchesVisitor<V> implements PathNodeVisitor<V> {
  private final Set<Bytes32> collectedBranches = new HashSet<>();
  private final GetVisitor<V> delegate = new GetVisitor<>();

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    collectedBranches.add(extensionNode.getHash());
    return delegate.visit(extensionNode, path);
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    collectedBranches.add(branchNode.getHash());
    return delegate.visit(branchNode, path);
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    collectedBranches.add(leafNode.getHash());
    return delegate.visit(leafNode, path);
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    collectedBranches.add(nullNode.getHash());
    return delegate.visit(nullNode, path);
  }

  public Set<Bytes32> getCollectedBranches() {
    return collectedBranches;
  }
}
