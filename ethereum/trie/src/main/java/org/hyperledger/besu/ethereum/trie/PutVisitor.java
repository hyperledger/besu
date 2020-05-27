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

class PutVisitor implements PathNodeVisitor {
  private final NodeFactory nodeFactory;
  private final Bytes value;

  PutVisitor(final NodeFactory nodeFactory, final Bytes value) {
    this.nodeFactory = nodeFactory;
    this.value = value;
  }

  @Override
  public Node visit(final ExtensionNode extensionNode, final Bytes path) {
    final Bytes extensionPath = extensionNode.getPath();

    final int commonPathLength = extensionPath.commonPrefixLength(path);
    assert commonPathLength < path.size()
        : "Visiting path doesn't end with a non-matching terminator";

    if (commonPathLength == extensionPath.size()) {
      final Node newChild = extensionNode.getChild().accept(this, path.slice(commonPathLength));
      return extensionNode.replaceChild(newChild);
    }

    // path diverges before the end of the extension - create a new branch

    final byte leafIndex = path.get(commonPathLength);
    final Bytes leafPath = path.slice(commonPathLength + 1);

    final byte extensionIndex = extensionPath.get(commonPathLength);
    final Node updatedExtension =
        extensionNode.replacePath(extensionPath.slice(commonPathLength + 1));
    final Node leaf = nodeFactory.createLeaf(leafPath, value);
    final Node branch = nodeFactory.createBranch(leafIndex, leaf, extensionIndex, updatedExtension);

    if (commonPathLength > 0) {
      return nodeFactory.createExtension(extensionPath.slice(0, commonPathLength), branch);
    } else {
      return branch;
    }
  }

  @Override
  public Node visit(final BranchNode branchNode, final Bytes path) {
    assert path.size() > 0 : "Visiting path doesn't end with a non-matching terminator";

    final byte childIndex = path.get(0);
    if (childIndex == CompactEncoding.LEAF_TERMINATOR) {
      return branchNode.replaceValue(value);
    }

    final Node updatedChild = branchNode.child(childIndex).accept(this, path.slice(1));
    return branchNode.replaceChild(childIndex, updatedChild);
  }

  @Override
  public Node visit(final LeafNode leafNode, final Bytes path) {
    final Bytes leafPath = leafNode.getPath();
    final int commonPathLength = leafPath.commonPrefixLength(path);

    // Check if the current leaf node should be replaced
    if (commonPathLength == leafPath.size() && commonPathLength == path.size()) {
      return nodeFactory.createLeaf(leafPath, value);
    }

    assert commonPathLength < leafPath.size() && commonPathLength < path.size()
        : "Should not have consumed non-matching terminator";

    // The current leaf path must be split to accommodate the new value.

    final byte newLeafIndex = path.get(commonPathLength);
    final Bytes newLeafPath = path.slice(commonPathLength + 1);

    final byte updatedLeafIndex = leafPath.get(commonPathLength);

    final Node updatedLeaf = leafNode.replacePath(leafPath.slice(commonPathLength + 1));
    final Node leaf = nodeFactory.createLeaf(newLeafPath, value);
    final Node branch = nodeFactory.createBranch(updatedLeafIndex, updatedLeaf, newLeafIndex, leaf);
    if (commonPathLength > 0) {
      return nodeFactory.createExtension(leafPath.slice(0, commonPathLength), branch);
    } else {
      return branch;
    }
  }

  @Override
  public Node visit(final NullNode nullNode, final Bytes path) {
    return nodeFactory.createLeaf(path, value);
  }
}
