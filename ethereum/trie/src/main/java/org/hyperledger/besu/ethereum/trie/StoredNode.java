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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class StoredNode<V> implements Node<V> {

  private final NodeFactory<V> nodeFactory;
  private byte[] location;
  private byte[] hash;
  private Node<V> loaded;

  // a child of a decoded branch copies its location and its hash out of the branch when needed
  private final byte[] branchLocation;
  private final byte childIndex;
  private final byte[] branchEncoding;
  private final int hashOffset;

  public StoredNode(final NodeFactory<V> nodeFactory, final byte[] location, final byte[] hash) {
    this.nodeFactory = nodeFactory;
    this.location = location;
    this.hash = hash;
    this.branchLocation = null;
    this.childIndex = 0;
    this.branchEncoding = null;
    this.hashOffset = 0;
  }

  /**
   * A child of a decoded branch, most of which are never visited.
   *
   * @param nodeFactory the node factory
   * @param branchLocation the location of the branch, null when unknown
   * @param childIndex the index of the child in the branch
   * @param branchEncoding the encoding holding the branch
   * @param hashOffset where the hash of the child is in {@code branchEncoding}
   */
  public StoredNode(
      final NodeFactory<V> nodeFactory,
      final byte[] branchLocation,
      final byte childIndex,
      final byte[] branchEncoding,
      final int hashOffset) {
    this.nodeFactory = nodeFactory;
    this.branchLocation = branchLocation;
    this.childIndex = childIndex;
    this.branchEncoding = branchEncoding;
    this.hashOffset = hashOffset;
  }

  public StoredNode(final NodeFactory<V> nodeFactory, final Bytes location, final Bytes32 hash) {
    this(nodeFactory, location == null ? null : location.toArray(), hash.toArray());
  }

  /**
   * @return True if the node needs to be persisted.
   */
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

  @Override
  public boolean isHealNeeded() {
    return false;
  }

  @Override
  public void markHealNeeded() {
    throw new IllegalStateException(
        "A stored node cannot be healed since it's loaded from storage");
  }

  @Override
  public Node<V> accept(final PathNodeVisitor<V> visitor, final byte[] path, final int offset) {
    final Node<V> node = load();
    return node.accept(visitor, path, offset);
  }

  @Override
  public void accept(final NodeVisitor<V> visitor) {
    final Node<V> node = load();
    node.accept(visitor);
  }

  @Override
  public void accept(final byte[] location, final LocationNodeVisitor<V> visitor) {
    final Node<V> node = load();
    node.accept(location, visitor);
  }

  @Override
  public byte[] path() {
    return load().path();
  }

  @Override
  public byte[] location() {
    byte[] childLocation = location;
    if (childLocation == null && branchLocation != null) {
      childLocation = Nibbles.append(branchLocation, childIndex);
      location = childLocation;
    }
    return childLocation;
  }

  @Override
  public Optional<V> getValue() {
    return load().getValue();
  }

  @Override
  public List<Node<V>> getChildren() {
    return load().getChildren();
  }

  @Override
  public byte[] encoded() {
    return load().encoded();
  }

  @Override
  public boolean isReferencedByHash() {
    // Stored nodes represent only nodes that are referenced by hash
    return true;
  }

  @Override
  public int encodedRefSize() {
    // If this node was stored, then it must have a rlp larger than a hash
    return TrieRlp.HASH_REF_SIZE;
  }

  @Override
  public int writeEncodedRef(final byte[] out, final int pos) {
    final byte[] childHash = hash;
    return childHash != null
        ? TrieRlp.writeHash(out, pos, childHash)
        : TrieRlp.writeHash(out, pos, branchEncoding, hashOffset);
  }

  @Override
  public byte[] hash() {
    byte[] childHash = hash;
    if (childHash == null) {
      childHash = Arrays.copyOfRange(branchEncoding, hashOffset, hashOffset + Keccak256.SIZE);
      hash = childHash;
    }
    return childHash;
  }

  @Override
  public Node<V> replacePath(final byte[] path) {
    return load().replacePath(path);
  }

  private Node<V> load() {
    if (loaded == null) {
      final byte[] nodeLocation = location();
      final byte[] nodeHash = hash();
      final Optional<Node<V>> node = nodeFactory.retrieve(nodeLocation, nodeHash);
      if (node.isEmpty()) {
        throw new MerkleTrieException(
            "Unable to load trie node value for hash "
                + Nibbles.toHexString(nodeHash)
                + " location "
                + Nibbles.toHexString(nodeLocation),
            Bytes32.wrap(nodeHash),
            nodeLocation == null ? null : Bytes.wrap(nodeLocation));
      }
      loaded = node.get();
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
      return "StoredNode:" + "\n\tRef: " + Nibbles.toHexString(encodedRef());
    } else {
      return load().print();
    }
  }
}
