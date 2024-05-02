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

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.patricia.BranchNode;
import org.hyperledger.besu.ethereum.trie.patricia.ExtensionNode;
import org.hyperledger.besu.ethereum.trie.patricia.StoredNodeFactory;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class SnapCommitVisitorTest {

  @Test
  public void shouldDetectValidBranch() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    final ArrayList<Node<Bytes>> children = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      children.add(
          new StoredNode<>(
              storedNodeFactory, Bytes.concatenate(Bytes.of(0x00), Bytes.of(i)), Hash.ZERO));
    }
    final BranchNode<Bytes> validBranchNode =
        new BranchNode<>(
            Bytes.of(0x00),
            children,
            Optional.of(Bytes.of(0x00)),
            storedNodeFactory,
            Function.identity());
    validBranchNode.markDirty();
    final SnapCommitVisitor<Bytes> snapCommitVisitor =
        new SnapCommitVisitor<>(
            (location, hash, value) -> {}, RangeManager.MIN_RANGE, RangeManager.MAX_RANGE);
    Assertions.assertThat(validBranchNode.isHealNeeded()).isFalse();
    snapCommitVisitor.visit(validBranchNode.getLocation().get(), validBranchNode);
    Assertions.assertThat(validBranchNode.isHealNeeded()).isFalse();
  }

  @Test
  public void shouldDetectBranchWithChildrenNotInTheRange() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    final ArrayList<Node<Bytes>> children = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      children.add(
          new StoredNode<>(
              storedNodeFactory, Bytes.concatenate(Bytes.of(0x01), Bytes.of(i)), Hash.ZERO));
    }

    final BranchNode<Bytes> invalidBranchNode =
        new BranchNode<>(
            Bytes.of(0x01),
            children,
            Optional.of(Bytes.of(0x00)),
            storedNodeFactory,
            Function.identity());
    invalidBranchNode.markDirty();
    final SnapCommitVisitor<Bytes> snapCommitVisitor =
        new SnapCommitVisitor<>(
            (location, hash, value) -> {},
            RangeManager.MIN_RANGE,
            Hash.fromHexString(
                "0x1effffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    Assertions.assertThat(invalidBranchNode.isHealNeeded()).isFalse();
    snapCommitVisitor.visit(invalidBranchNode.getLocation().get(), invalidBranchNode);
    Assertions.assertThat(invalidBranchNode.isHealNeeded()).isTrue();
  }

  @Test
  public void shouldDetectValidExtension() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    final ExtensionNode<Bytes> validExtensionNode =
        new ExtensionNode<>(
            Bytes.of(0x00),
            Bytes.of(0x01),
            new StoredNode<>(storedNodeFactory, Bytes.of((byte) 0x00, (byte) 0x01), Hash.ZERO),
            storedNodeFactory);
    validExtensionNode.markDirty();
    final SnapCommitVisitor<Bytes> snapCommitVisitor =
        new SnapCommitVisitor<>(
            (location, hash, value) -> {}, RangeManager.MIN_RANGE, RangeManager.MAX_RANGE);
    Assertions.assertThat(validExtensionNode.isHealNeeded()).isFalse();
    snapCommitVisitor.visit(validExtensionNode.getLocation().get(), validExtensionNode);
    Assertions.assertThat(validExtensionNode.isHealNeeded()).isFalse();
  }

  @Test
  public void shouldDetectExtensionWithChildNotInRange() {
    final StoredNodeFactory<Bytes> storedNodeFactory = mock(StoredNodeFactory.class);
    final ExtensionNode<Bytes> inValidExtensionNode =
        new ExtensionNode<>(
            Bytes.of(0x00),
            Bytes.of(0x03),
            new StoredNode<>(storedNodeFactory, Bytes.of((byte) 0x00, (byte) 0x03), Hash.ZERO),
            storedNodeFactory);
    inValidExtensionNode.markDirty();
    final SnapCommitVisitor<Bytes> snapCommitVisitor =
        new SnapCommitVisitor<>(
            (location, hash, value) -> {},
            RangeManager.MIN_RANGE,
            Hash.fromHexString(
                "0x02ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    Assertions.assertThat(inValidExtensionNode.isHealNeeded()).isFalse();
    snapCommitVisitor.visit(inValidExtensionNode.getLocation().get(), inValidExtensionNode);
    Assertions.assertThat(inValidExtensionNode.isHealNeeded()).isTrue();
  }
}
