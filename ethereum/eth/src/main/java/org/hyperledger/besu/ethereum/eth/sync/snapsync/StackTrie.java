/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.SnapPutVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public class StackTrie {

  private final Bytes32 rootHash;
  private final AtomicInteger nbSegments;
  private final int maxSegments;
  private final Bytes32 startKeyHash;
  private final Map<Bytes32, TaskElement> elements;
  private final AtomicLong elementsCount;

  public StackTrie(final Hash rootHash, final Bytes32 startKeyHash) {
    this(rootHash, 1, 1, startKeyHash);
  }

  public StackTrie(
      final Hash rootHash,
      final int nbSegments,
      final int maxSegments,
      final Bytes32 startKeyHash) {
    this.rootHash = rootHash;
    this.nbSegments = new AtomicInteger(nbSegments);
    this.maxSegments = maxSegments;
    this.startKeyHash = startKeyHash;
    this.elements = new LinkedHashMap<>();
    this.elementsCount = new AtomicLong();
  }

  public void addElement(
      final Bytes32 taskIdentifier, final List<Bytes> proofs, final TreeMap<Bytes32, Bytes> keys) {
    this.elementsCount.addAndGet(keys.size());
    this.elements.put(
        taskIdentifier, ImmutableTaskElement.builder().proofs(proofs).keys(keys).build());
  }

  public TaskElement getElement(final Bytes32 taskIdentifier) {
    return this.elements.get(taskIdentifier);
  }

  public AtomicLong getElementsCount() {
    return elementsCount;
  }

  public void commit(final NodeUpdater nodeUpdater) {

    if (nbSegments.decrementAndGet() <= 0 && !elements.isEmpty()) {

      final List<Bytes> proofs = new ArrayList<>();
      final TreeMap<Bytes32, Bytes> keys = new TreeMap<>();

      elements
          .values()
          .forEach(
              taskElement -> {
                proofs.addAll(taskElement.proofs());
                keys.putAll(taskElement.keys());
              });

      final Map<Bytes32, Bytes> proofsEntries = new HashMap<>();
      for (Bytes proof : proofs) {
        proofsEntries.put(Hash.hash(proof), proof);
      }

      final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
          new InnerNodeDiscoveryManager<>(
              (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
              Function.identity(),
              Function.identity(),
              startKeyHash,
              keys.lastKey(),
              true);

      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(
              snapStoredNodeFactory,
              proofs.isEmpty() ? MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH : rootHash);

      for (Map.Entry<Bytes32, Bytes> account : keys.entrySet()) {
        trie.put(account.getKey(), new SnapPutVisitor<>(snapStoredNodeFactory, account.getValue()));
      }
      trie.commit(
          nodeUpdater,
          (new CommitVisitor<>(nodeUpdater) {
            @Override
            public void maybeStoreNode(final Bytes location, final Node<Bytes> node) {
              if (!node.isHealNeeded()) {
                super.maybeStoreNode(location, node);
              }
            }
          }));
    }
  }

  public boolean addSegment() {
    if (nbSegments.get() > maxSegments) {
      return false;
    } else {
      nbSegments.incrementAndGet();
      return true;
    }
  }

  @Value.Immutable
  public abstract static class TaskElement {

    @Value.Default
    public List<Bytes> proofs() {
      return new ArrayList<>();
    }

    @Value.Default
    public TreeMap<Bytes32, Bytes> keys() {
      return new TreeMap<>();
    }
  }
}
