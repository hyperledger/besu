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
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface NodeFactory<V> {

  default Node<V> createExtension(final Bytes path, final Node<V> child) {
    return createExtension(Optional.empty(), path, child);
  }

  default Node<V> createBranch(
      final byte leftIndex, final Node<V> left, final byte rightIndex, final Node<V> right) {
    return createBranch(Optional.empty(), leftIndex, left, rightIndex, right);
  }

  default Node<V> createBranch(final ArrayList<Node<V>> newChildren, final Optional<V> value) {
    return createBranch(Optional.empty(), newChildren, value);
  }

  default Node<V> createLeaf(final Bytes path, final V value) {
    return createLeaf(Optional.empty(), path, value);
  }

  Node<V> createExtension(Optional<Bytes> location, Bytes path, Node<V> child);

  Node<V> createBranch(
      Optional<Bytes> location, byte leftIndex, Node<V> left, byte rightIndex, Node<V> right);

  Node<V> createBranch(Optional<Bytes> location, ArrayList<Node<V>> newChildren, Optional<V> value);

  Node<V> createLeaf(Optional<Bytes> location, Bytes path, V value);
}
