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

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

class BranchNode implements Node {
  public static final byte RADIX = CompactEncoding.LEAF_TERMINATOR;

  @SuppressWarnings("rawtypes")
  private static final Node NULL_NODE = NullNode.instance();

  private final ArrayList<Node> children;
  private final Optional<Bytes> value;
  private final NodeFactory nodeFactory;
  private WeakReference<Bytes> rlp;
  private SoftReference<Bytes32> hash;
  private boolean dirty = false;

  BranchNode(
      final ArrayList<Node> children, final Optional<Bytes> value, final NodeFactory nodeFactory) {
    assert (children.size() == RADIX);
    this.children = children;
    this.value = value;
    this.nodeFactory = nodeFactory;
  }

  @Override
  public Node accept(final PathNodeVisitor visitor, final Bytes path) {
    return visitor.visit(this, path);
  }

  @Override
  public void accept(final NodeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public Bytes getPath() {
    return Bytes.EMPTY;
  }

  @Override
  public Optional<Bytes> getValue() {
    return value;
  }

  @Override
  public List<Node> getChildren() {
    return Collections.unmodifiableList(children);
  }

  public Node child(final byte index) {
    return children.get(index);
  }

  @Override
  public Bytes getRlp() {
    if (rlp != null) {
      final Bytes encoded = rlp.get();
      if (encoded != null) {
        return encoded;
      }
    }
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    for (int i = 0; i < RADIX; ++i) {
      out.writeRLPUnsafe(children.get(i).getRlpRef());
    }
    if (value.isPresent()) {
      out.writeBytes(value.get());
    } else {
      out.writeNull();
    }
    out.endList();
    final Bytes encoded = out.encoded();
    rlp = new WeakReference<>(encoded);
    return encoded;
  }

  @Override
  public Bytes getRlpRef() {
    if (isReferencedByHash()) {
      return RLP.encodeOne(getHash());
    } else {
      return getRlp();
    }
  }

  @Override
  public Bytes32 getHash() {
    if (hash != null) {
      final Bytes32 hashed = hash.get();
      if (hashed != null) {
        return hashed;
      }
    }
    final Bytes32 hashed = keccak256(getRlp());
    hash = new SoftReference<>(hashed);
    return hashed;
  }

  @Override
  public Node replacePath(final Bytes newPath) {
    return nodeFactory.createExtension(newPath, this);
  }

  public Node replaceChild(final byte index, final Node updatedChild) {
    final ArrayList<Node> newChildren = new ArrayList<>(children);
    newChildren.set(index, updatedChild);

    if (updatedChild == NULL_NODE) {
      if (value.isPresent() && !hasChildren()) {
        return nodeFactory.createLeaf(Bytes.of(index), value.get());
      } else if (!value.isPresent()) {
        final Optional<Node> flattened = maybeFlatten(newChildren);
        if (flattened.isPresent()) {
          return flattened.get();
        }
      }
    }

    return nodeFactory.createBranch(newChildren, value);
  }

  public Node replaceValue(final Bytes value) {
    return nodeFactory.createBranch(children, Optional.of(value));
  }

  public Node removeValue() {
    return maybeFlatten(children).orElse(nodeFactory.createBranch(children, Optional.empty()));
  }

  private boolean hasChildren() {
    for (final Node child : children) {
      if (child != NULL_NODE) {
        return true;
      }
    }
    return false;
  }

  private static Optional<Node> maybeFlatten(final ArrayList<Node> children) {
    final int onlyChildIndex = findOnlyChild(children);
    if (onlyChildIndex >= 0) {
      // replace the path of the only child and return it
      final Node onlyChild = children.get(onlyChildIndex);
      final Bytes onlyChildPath = onlyChild.getPath();
      final MutableBytes completePath = MutableBytes.create(1 + onlyChildPath.size());
      completePath.set(0, (byte) onlyChildIndex);
      onlyChildPath.copyTo(completePath, 1);
      return Optional.of(onlyChild.replacePath(completePath));
    }
    return Optional.empty();
  }

  private static int findOnlyChild(final ArrayList<Node> children) {
    int onlyChildIndex = -1;
    assert (children.size() == RADIX);
    for (int i = 0; i < RADIX; ++i) {
      if (children.get(i) != NULL_NODE) {
        if (onlyChildIndex >= 0) {
          return -1;
        }
        onlyChildIndex = i;
      }
    }
    return onlyChildIndex;
  }

  @Override
  public String print() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Branch:");
    builder.append("\n\tRef: ").append(getRlpRef());
    for (int i = 0; i < RADIX; i++) {
      final Node child = child((byte) i);
      if (!Objects.equals(child, NullNode.instance())) {
        final String branchLabel = "[" + Integer.toHexString(i) + "] ";
        final String childRep = child.print().replaceAll("\n\t", "\n\t\t");
        builder.append("\n\t").append(branchLabel).append(childRep);
      }
    }
    builder.append("\n\tValue: ").append(getValue().map(Object::toString).orElse("empty"));
    return builder.toString();
  }

  @Override
  public boolean isDirty() {
    return dirty;
  }

  @Override
  public void markDirty() {
    dirty = true;
  }
}
