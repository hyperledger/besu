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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

class DefaultNodeFactory implements NodeFactory {
  @SuppressWarnings("rawtypes")
  private static final Node NULL_NODE = NullNode.instance();

  DefaultNodeFactory() {}

  @Override
  public Node createExtension(final Bytes path, final Node child) {
    return new ExtensionNode(path, child, this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node createBranch(
      final byte leftIndex, final Node left, final byte rightIndex, final Node right) {
    assert (leftIndex <= BranchNode.RADIX);
    assert (rightIndex <= BranchNode.RADIX);
    assert (leftIndex != rightIndex);

    final ArrayList<Node> children =
        new ArrayList<>(Collections.nCopies(BranchNode.RADIX, NULL_NODE));
    if (leftIndex == BranchNode.RADIX) {
      children.set(rightIndex, right);
      return createBranch(children, left.getValue());
    } else if (rightIndex == BranchNode.RADIX) {
      children.set(leftIndex, left);
      return createBranch(children, right.getValue());
    } else {
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(children, Optional.empty());
    }
  }

  @Override
  public Node createBranch(final ArrayList<Node> children, final Optional<Bytes> value) {
    return new BranchNode(children, value, this);
  }

  @Override
  public Node createLeaf(final Bytes path, final Bytes value) {
    return new LeafNode(path, value, this);
  }
}
