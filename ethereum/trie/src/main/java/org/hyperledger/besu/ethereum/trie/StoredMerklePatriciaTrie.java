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
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A {@link MerklePatriciaTrie} that persists trie nodes to a {@link MerkleStorage} key/value store.
 */
public class StoredMerklePatriciaTrie<K extends Bytes> implements MerklePatriciaTrie<K> {
  private final GetVisitor getVisitor = new GetVisitor();
  private final RemoveVisitor removeVisitor = new RemoveVisitor();
  private final StoredNodeFactory nodeFactory;

  private Node root;

  /**
   * Create a trie.
   *
   * @param nodeLoader The {@link NodeLoader} to retrieve node data from.
   */
  public StoredMerklePatriciaTrie(final NodeLoader nodeLoader) {
    this(nodeLoader, EMPTY_TRIE_NODE_HASH);
  }

  /**
   * Create a trie.
   *
   * @param nodeLoader The {@link NodeLoader} to retrieve node data from.
   * @param rootHash The initial root has for the trie, which should be already present in {@code
   *     storage}.
   */
  public StoredMerklePatriciaTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    this.nodeFactory = new StoredNodeFactory(nodeLoader);
    this.root =
        rootHash.equals(EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode(nodeFactory, rootHash);
  }

  @Override
  public Optional<Bytes> get(final K key) {
    checkNotNull(key);
    return root.accept(getVisitor, bytesToPath(key)).getValue();
  }

  @Override
  public Proof<Bytes> getValueWithProof(final K key) {
    checkNotNull(key);
    final ProofVisitor proofVisitor = new ProofVisitor(root);
    final Optional<Bytes> value = root.accept(proofVisitor, bytesToPath(key)).getValue();
    final List<Bytes> proof =
        proofVisitor.getProof().stream().map(Node::getRlp).collect(Collectors.toList());
    return new Proof<Bytes>(value, proof);
  }

  @Override
  public void put(final K key, final Bytes value) {
    checkNotNull(key);
    checkNotNull(value);
    this.root = root.accept(new PutVisitor(nodeFactory, value), bytesToPath(key));
  }

  @Override
  public void remove(final K key) {
    checkNotNull(key);
    this.root = root.accept(removeVisitor, bytesToPath(key));
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    final CommitVisitor commitVisitor = new CommitVisitor(nodeUpdater);
    root.accept(commitVisitor);
    // Make sure root node was stored
    if (root.isDirty() && root.getRlpRef().size() < 32) {
      nodeUpdater.store(root.getHash(), root.getRlpRef());
    }
    // Reset root so dirty nodes can be garbage collected
    final Bytes32 rootHash = root.getHash();
    this.root =
        rootHash.equals(EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode(nodeFactory, rootHash);
  }

  @Override
  public Map<Bytes32, Bytes> entriesFrom(final Bytes32 startKeyHash, final int limit) {
    return StorageEntriesCollector.collectEntries(root, startKeyHash, limit);
  }

  @Override
  public void visitAll(final Consumer<Node> visitor) {
    root.accept(new AllNodesVisitor(visitor));
  }

  @Override
  public Bytes32 getRootHash() {
    return root.getHash();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + getRootHash() + "]";
  }
}
