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
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class LeafNode<V> implements Node<V> {
  private final BytesValue path;
  private final V value;
  private final NodeFactory<V> nodeFactory;
  private final Function<V, BytesValue> valueSerializer;
  private WeakReference<BytesValue> rlp;
  private SoftReference<Bytes32> hash;
  private boolean dirty = false;

  LeafNode(
      final BytesValue path,
      final V value,
      final NodeFactory<V> nodeFactory,
      final Function<V, BytesValue> valueSerializer) {
    this.path = path;
    this.value = value;
    this.nodeFactory = nodeFactory;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public Node<V> accept(final PathNodeVisitor<V> visitor, final BytesValue path) {
    return visitor.visit(this, path);
  }

  @Override
  public void accept(final NodeVisitor<V> visitor) {
    visitor.visit(this);
  }

  @Override
  public BytesValue getPath() {
    return path;
  }

  @Override
  public Optional<V> getValue() {
    return Optional.of(value);
  }

  @Override
  public List<Node<V>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public BytesValue getRlp() {
    if (rlp != null) {
      final BytesValue encoded = rlp.get();
      if (encoded != null) {
        return encoded;
      }
    }

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytesValue(CompactEncoding.encode(path));
    out.writeBytesValue(valueSerializer.apply(value));
    out.endList();
    final BytesValue encoded = out.encoded();
    rlp = new WeakReference<>(encoded);
    return encoded;
  }

  @Override
  public BytesValue getRlpRef() {
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
  public Node<V> replacePath(final BytesValue path) {
    return nodeFactory.createLeaf(path, value);
  }

  @Override
  public String print() {
    return "Leaf:"
        + "\n\tRef: "
        + getRlpRef()
        + "\n\tPath: "
        + CompactEncoding.encode(path)
        + "\n\tValue: "
        + getValue().map(Object::toString).orElse("empty");
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
