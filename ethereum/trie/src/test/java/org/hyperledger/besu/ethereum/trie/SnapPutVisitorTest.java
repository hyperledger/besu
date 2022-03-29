/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.data.Hash;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class SnapPutVisitorTest {

  @Test
  public void shouldDetectValidBranch() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    when(storedNodeFactory.createBranch(any(), any()))
        .thenReturn(
            new LeafNode<Bytes>(
                Bytes.EMPTY, Bytes.of(0x00), storedNodeFactory, Function.identity()));
    final ArrayList<Node<Bytes>> children = new ArrayList<>();
    for (int i = 0; i < BranchNode.RADIX; i++) {
      children.add(new StoredNode<>(storedNodeFactory, Bytes.EMPTY, Hash.ZERO));
    }
    final BranchNode<Bytes> invalidBranchNode =
        new BranchNode<>(
            Bytes.EMPTY,
            children,
            Optional.of(Bytes.of(0x00)),
            storedNodeFactory,
            Function.identity());
    final SnapPutVisitor<Bytes> snapPutVisitor =
        new SnapPutVisitor<>(storedNodeFactory, Bytes.EMPTY);
    Node<Bytes> visit =
        snapPutVisitor.visit(invalidBranchNode, Bytes.of(CompactEncoding.LEAF_TERMINATOR));
    Assertions.assertThat(visit.isHealNeeded()).isFalse();
  }

  @Test
  public void shouldDetectBranchWithMissingChildren() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    when(storedNodeFactory.createBranch(any(), any()))
        .thenReturn(new MissingNode<>(Hash.ZERO, Bytes.EMPTY));
    final ArrayList<Node<Bytes>> children = new ArrayList<>();
    for (int i = 0; i < BranchNode.RADIX; i++) {
      children.add(new StoredNode<>(storedNodeFactory, Bytes.EMPTY, Hash.ZERO));
    }
    final BranchNode<Bytes> invalidBranchNode =
        new BranchNode<>(
            Bytes.EMPTY,
            children,
            Optional.of(Bytes.of(0x00)),
            storedNodeFactory,
            Function.identity());
    final SnapPutVisitor<Bytes> snapPutVisitor =
        new SnapPutVisitor<>(storedNodeFactory, Bytes.EMPTY);
    Node<Bytes> visit =
        snapPutVisitor.visit(invalidBranchNode, Bytes.of(CompactEncoding.LEAF_TERMINATOR));
    Assertions.assertThat(visit.isHealNeeded()).isTrue();
  }

  @Test
  public void shouldDetectValidExtension() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    when(storedNodeFactory.createBranch(any(), any()))
        .thenReturn(
            new LeafNode<>(Bytes.EMPTY, Bytes.of(0x00), storedNodeFactory, Function.identity()));
    final ArrayList<Node<Bytes>> children = new ArrayList<>();
    for (int i = 0; i < BranchNode.RADIX; i++) {
      children.add(new StoredNode<>(storedNodeFactory, Bytes.EMPTY, Hash.ZERO));
    }
    final BranchNode<Bytes> invalidBranchNode =
        new BranchNode<>(
            Bytes.EMPTY,
            children,
            Optional.of(Bytes.of(0x00)),
            storedNodeFactory,
            Function.identity());
    final SnapPutVisitor<Bytes> snapPutVisitor =
        new SnapPutVisitor<>(storedNodeFactory, Bytes.EMPTY);
    Node<Bytes> visit =
        snapPutVisitor.visit(invalidBranchNode, Bytes.of(CompactEncoding.LEAF_TERMINATOR));
    Assertions.assertThat(visit.isHealNeeded()).isFalse();
  }

  @Test
  public void shouldDetectExtensionWithMissingChildren() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    when(storedNodeFactory.createBranch(anyByte(), any(), anyByte(), any()))
        .thenReturn(new MissingNode<>(Hash.ZERO, Bytes.EMPTY));
    when(storedNodeFactory.createLeaf(any(), any()))
        .thenReturn(new MissingNode<>(Hash.ZERO, Bytes.EMPTY));
    final ExtensionNode<Bytes> invalidBranchNode =
        new ExtensionNode<>(
            Bytes.of(0x00),
            new StoredNode<>(storedNodeFactory, Bytes.EMPTY, Hash.ZERO),
            storedNodeFactory);
    final SnapPutVisitor<Bytes> snapPutVisitor =
        new SnapPutVisitor<>(storedNodeFactory, Bytes.EMPTY);
    Node<Bytes> visit =
        snapPutVisitor.visit(invalidBranchNode, Bytes.of(CompactEncoding.LEAF_TERMINATOR));
    Assertions.assertThat(visit.isHealNeeded()).isTrue();
  }
}
