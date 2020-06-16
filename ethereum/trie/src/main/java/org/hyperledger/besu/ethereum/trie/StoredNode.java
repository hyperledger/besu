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

import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class StoredNode implements Node {
  private final StoredNodeFactory nodeFactory;
  private final Bytes32 hash;
  private Node loaded;

  StoredNode(final StoredNodeFactory nodeFactory, final Bytes32 hash) {
    this.nodeFactory = nodeFactory;
    this.hash = hash;
  }

  /** @return True if the node needs to be persisted. */
  @Override
  public boolean isDirty() {
    return false;
  }

  /** Marks the node as being modified (needs to be persisted); */
  @Override
  public void markDirty() {
    throw new IllegalStateException(
        "A stored node cannot ever be dirty since it's loaded from storage");
  }

  /**
   * TODO: This is added to appease the compile Gods. If everything works, this entire class goes
   * away.
   */
  @Override
  public Stream<Bytes32> accept(final NodeHashStreamer visitor) {
    final Node node = load();
    return node.accept(visitor);
  }

  @Override
  public Node accept(final PathNodeVisitor visitor, final Bytes path) {
    final Node node = load();
    return node.accept(visitor, path);
  }

  @Override
  public void accept(final NodeVisitor visitor) {
    final Node node = load();
    node.accept(visitor);
  }

  @Override
  public Bytes getPath() {
    return load().getPath();
  }

  @Override
  public Optional<Bytes> getValue() {
    return load().getValue();
  }

  @Override
  public List<Node> getChildren() {
    return load().getChildren();
  }

  @Override
  public Bytes getRlp() {
    return load().getRlp();
  }

  @Override
  public Bytes getRlpRef() {
    // If this node was stored, then it must have a rlp larger than a hash
    return RLP.encodeOne(hash);
  }

  @Override
  public boolean isReferencedByHash() {
    // Stored nodes represent only nodes that are referenced by hash
    return true;
  }

  @Override
  public Bytes32 getHash() {
    return hash;
  }

  @Override
  public Node replacePath(final Bytes path) {
    return load().replacePath(path);
  }

  private Node load() {
    if (loaded == null) {
      loaded =
          nodeFactory
              .retrieve(hash)
              .orElseThrow(
                  () -> new MerkleTrieException("Unable to load trie node value for hash " + hash));
    }

    return loaded;
  }

  @Override
  public void unload() {
    loaded = null;
  }

  @Override
  public String print() {
    if (loaded == null) {
      return "StoredNode:" + "\n\tRef: " + getRlpRef();
    } else {
      return load().print();
    }
  }
}
