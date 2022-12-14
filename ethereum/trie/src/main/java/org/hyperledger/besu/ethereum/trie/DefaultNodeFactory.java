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
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

public class DefaultNodeFactory<V> implements NodeFactory<V> {
  @SuppressWarnings("rawtypes")
  private static final Node NULL_NODE = NullNode.instance();

  private final Function<V, Bytes> valueSerializer;

  public DefaultNodeFactory(final Function<V, Bytes> valueSerializer) {
    this.valueSerializer = valueSerializer;
  }

  @Override
  public Node<V> createExtension(
      final Optional<Bytes> location, final Bytes path, final Node<V> child) {
    return new ExtensionNode<>(location, path, child, this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node<V> createBranch(
      final Optional<Bytes> location,
      final byte leftIndex,
      final Node<V> left,
      final byte rightIndex,
      final Node<V> right) {
    assert (leftIndex <= BranchNode.RADIX);
    assert (rightIndex <= BranchNode.RADIX);
    assert (leftIndex != rightIndex);

    final ArrayList<Node<V>> children =
        new ArrayList<>(Collections.nCopies(BranchNode.RADIX, (Node<V>) NULL_NODE));
    if (leftIndex == BranchNode.RADIX) {
      children.set(rightIndex, right);
      return createBranch(location, children, left.getValue());
    } else if (rightIndex == BranchNode.RADIX) {
      children.set(leftIndex, left);
      return createBranch(location, children, right.getValue());
    } else {
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(location, children, Optional.empty());
    }
  }

  @Override
  public Node<V> createBranch(
      final Optional<Bytes> location, final ArrayList<Node<V>> children, final Optional<V> value) {
    return new BranchNode<>(location, children, value, this, valueSerializer);
  }

  @Override
  public Node<V> createLeaf(final Optional<Bytes> location, final Bytes path, final V value) {
    return new LeafNode<>(location, path, value, this, valueSerializer);
  }
}
