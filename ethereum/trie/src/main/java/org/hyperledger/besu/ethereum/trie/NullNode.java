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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class NullNode implements Node {
  private static final NullNode instance = new NullNode();

  private NullNode() {}

  static NullNode instance() {
    return instance;
  }

  @Override
  public Stream<Bytes32> accept(final NodeHashStreamer visitor) {
    return visitor.visit(this);
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
    return Optional.empty();
  }

  @Override
  public List<Node> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public Bytes getRlp() {
    return MerklePatriciaTrie.EMPTY_TRIE_NODE;
  }

  @Override
  public Bytes getRlpRef() {
    return MerklePatriciaTrie.EMPTY_TRIE_NODE;
  }

  @Override
  public Bytes32 getHash() {
    return MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH;
  }

  @Override
  public Node replacePath(final Bytes path) {
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
