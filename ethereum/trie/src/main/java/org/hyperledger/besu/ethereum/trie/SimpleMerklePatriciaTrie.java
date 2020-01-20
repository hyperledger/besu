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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * An in-memory {@link MerklePatriciaTrie}.
 *
 * @param <V> The type of values stored by this trie.
 */
public class SimpleMerklePatriciaTrie<K extends Bytes, V> implements MerklePatriciaTrie<K, V> {
  private final PathNodeVisitor<V> getVisitor = new GetVisitor<>();
  private final PathNodeVisitor<V> removeVisitor = new RemoveVisitor<>();
  private final DefaultNodeFactory<V> nodeFactory;

  private Node<V> root;

  /**
   * Create a trie.
   *
   * @param valueSerializer A function for serializing values to bytes.
   */
  public SimpleMerklePatriciaTrie(final Function<V, Bytes> valueSerializer) {
    this.nodeFactory = new DefaultNodeFactory<>(valueSerializer);
    this.root = NullNode.instance();
  }

  @Override
  public Optional<V> get(final K key) {
    checkNotNull(key);
    return root.accept(getVisitor, bytesToPath(key)).getValue();
  }

  @Override
  public Proof<V> getValueWithProof(final K key) {
    checkNotNull(key);
    final ProofVisitor<V> proofVisitor = new ProofVisitor<>(root);
    final Optional<V> value = root.accept(proofVisitor, bytesToPath(key)).getValue();
    final List<Bytes> proof =
        proofVisitor.getProof().stream().map(Node::getRlp).collect(Collectors.toList());
    return new Proof<>(value, proof);
  }

  @Override
  public void put(final K key, final V value) {
    checkNotNull(key);
    checkNotNull(value);
    this.root = root.accept(new PutVisitor<>(nodeFactory, value), bytesToPath(key));
  }

  @Override
  public void remove(final K key) {
    checkNotNull(key);
    this.root = root.accept(removeVisitor, bytesToPath(key));
  }

  @Override
  public Bytes32 getRootHash() {
    return root.getHash();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + getRootHash() + "]";
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    // Nothing to do here
  }

  @Override
  public Map<Bytes32, V> entriesFrom(final Bytes32 startKeyHash, final int limit) {
    return StorageEntriesCollector.collectEntries(root, startKeyHash, limit);
  }

  @Override
  public void visitAll(final Consumer<Node<V>> visitor) {
    root.accept(new AllNodesVisitor<>(visitor));
  }
}
