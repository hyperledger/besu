/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.trie;

import static tech.pegasys.pantheon.crypto.Hash.keccak256;

import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

class NullNode<V> implements Node<V> {
  private static final Bytes32 HASH = keccak256(RLP.NULL);

  @SuppressWarnings("rawtypes")
  private static final NullNode instance = new NullNode();

  private NullNode() {}

  @SuppressWarnings("unchecked")
  static <V> NullNode<V> instance() {
    return instance;
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
    return BytesValue.EMPTY;
  }

  @Override
  public Optional<V> getValue() {
    return Optional.empty();
  }

  @Override
  public BytesValue getRlp() {
    return RLP.NULL;
  }

  @Override
  public BytesValue getRlpRef() {
    return RLP.NULL;
  }

  @Override
  public Bytes32 getHash() {
    return HASH;
  }

  @Override
  public Node<V> replacePath(final BytesValue path) {
    return this;
  }

  @Override
  public String print() {
    return "[NULL]";
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void markDirty() {
    // do nothing
  }
}
