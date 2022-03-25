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
package org.hyperledger.besu.ethereum.trie;

import org.apache.tuweni.bytes.Bytes;

public class SnapPutVisitor<V> extends PutVisitor<V> {

  public SnapPutVisitor(final NodeFactory<V> nodeFactory, final V value) {
    super(nodeFactory, value);
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    final Node<V> visit = super.visit(branchNode, path);
    for (Node<V> child : visit.getChildren()) {
      if (child.isHealNeeded() || (child instanceof StoredNode && child.getValue().isEmpty())) {
        visit.markHealNeeded(); // not save an incomplete node
        return visit;
      }
    }
    return visit;
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    final Node<V> visit = super.visit(extensionNode, path);
    for (Node<V> child : visit.getChildren()) {
      if (child.isHealNeeded() || (child instanceof StoredNode && child.getValue().isEmpty())) {
        visit.markHealNeeded(); // not save an incomplete node
        return visit;
      }
    }
    return visit;
  }
}
