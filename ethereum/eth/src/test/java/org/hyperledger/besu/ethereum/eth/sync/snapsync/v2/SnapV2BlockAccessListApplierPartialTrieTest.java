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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.DownloadedAccountRangeTracker;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.DownloadedStorageRangeTracker;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncMetricsManager;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.StubTask;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapRequestContext;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.v2.SnapV2AccountRangeRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeManager;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives real snap/2 account range commits and BAL catch-ups against Bonsai storage, where some
 * account subtrees are only partially downloaded when the pivot moves.
 */
class SnapV2BlockAccessListApplierPartialTrieTest {

  private final ReorgBlockchainBuilder chain = new ReorgBlockchainBuilder();
  private final BonsaiWorldStateKeyValueStorage storage =
      new BonsaiWorldStateKeyValueStorage(
          new InMemoryKeyValueStorageProvider(),
          new NoOpMetricsSystem(),
          DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
  private final WorldStateStorageCoordinator coordinator =
      new WorldStateStorageCoordinator(storage);
  private final DownloadedAccountRangeTracker accountTracker = new DownloadedAccountRangeTracker();
  private final DownloadedStorageRangeTracker storageTracker = new DownloadedStorageRangeTracker();
  private final SnapV2BlockAccessListApplier applier =
      new SnapV2BlockAccessListApplier(
          coordinator, chain.blockchain(), ReorgBlockchainBuilder.balEnabledSchedule());
  private final WorldStateProofProvider proofProvider = new WorldStateProofProvider(coordinator);
  private final List<SnapDataRequest> enqueued = new ArrayList<>();

  // Canonical account state at the current pivot, as served by peers.
  private final NavigableMap<Bytes32, Bytes> peerAccounts = new TreeMap<>();
  private final Map<Bytes32, Address> addressByHash = new HashMap<>();
  private BlockHeader pivot;
  private SnapV2PersistDataStep persistStep;

  @BeforeEach
  void setUp() {
    final Random random = new Random(42);
    for (int i = 0; i < 200; i++) {
      final Address address = Address.wrap(Bytes.random(20, random));
      addressByHash.put(hash(address), address);
      peerAccounts.put(hash(address), account(Wei.ONE));
    }
    pivot = chain.appendCanonical(chain.header(0), chain.emptyBal(), 1L, peerRoot()).getHeader();

    final SnapSyncProcessState snapSyncState = mock(SnapSyncProcessState.class);
    when(snapSyncState.getPivotBlockHash()).thenAnswer(inv -> Optional.of(pivot.getHash()));
    final SnapRequestContext downloadState = mock(SnapRequestContext.class);
    when(downloadState.getMetricsManager()).thenReturn(mock(SnapSyncMetricsManager.class));
    doAnswer(
            inv -> {
              final Stream<SnapDataRequest> children = inv.getArgument(0);
              children.forEach(enqueued::add);
              return null;
            })
        .when(downloadState)
        .enqueueRequests(any());
    persistStep =
        new SnapV2PersistDataStep(
            snapSyncState,
            coordinator,
            downloadState,
            SnapSyncConfiguration.getDefault(),
            accountTracker,
            storageTracker);
  }

  /**
   * A range commit at the new pivot rewrites the root with the canonical hash of a top-level
   * subtree that is still being downloaded, while the local copy of that subtree is the mix of
   * pivots left by the previous catch-up. The next catch-up must still apply BALs to the persisted
   * accounts of that subtree.
   */
  @Test
  void appliesBalsUnderSubtreeStillBeingDownloadedAfterRootIsRewritten() {
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(2);
    final Bytes32 lowStart = ranges.keySet().stream().min(Bytes32::compareTo).orElseThrow();
    final Bytes32 highStart = ranges.keySet().stream().max(Bytes32::compareTo).orElseThrow();

    // Stop the high frontier in the middle of a top-level subtree holding at least 3 accounts. It
    // skips the first high subtree, which the low frontier's last response reaches into.
    final List<Bytes32> highAccounts = new ArrayList<>(peerAccounts.tailMap(highStart).keySet());
    final int nibble = firstNibbleWithAtLeast(highAccounts, firstNibble(highStart) + 1, 3);
    final List<Bytes32> inNibble =
        highAccounts.stream().filter(h -> firstNibble(h) == nibble).toList();
    final Bytes32 persisted = inNibble.get(1);
    final Bytes32 notPersisted = inNibble.get(2);

    SnapV2AccountRangeRequest low =
        download(new SnapV2AccountRangeRequest(pivot, lowStart, ranges.get(lowStart)), 5);
    SnapV2AccountRangeRequest high =
        download(
            new SnapV2AccountRangeRequest(pivot, highStart, ranges.get(highStart)),
            highAccounts.indexOf(persisted) + 1);
    assertThat(accountTracker.isAccountHashPersisted(persisted)).isTrue();
    assertThat(accountTracker.isAccountHashPersisted(notPersisted)).isFalse();

    // Catch-up 1: touches both halves of the partially downloaded subtree.
    catchUp(Map.of(persisted, Wei.of(100), notPersisted, Wei.of(200)));
    low = low.retarget(pivot);
    high = high.retarget(pivot);

    // The low frontier finishes at the new pivot, rewriting the root; the high frontier stalls.
    while (low != null) {
      low = download(low, 20);
    }

    // Catch-up 2: walks into the stalled subtree through the rewritten root.
    catchUp(Map.of(persisted, Wei.of(300)));
    high = high.retarget(pivot);

    while (high != null) {
      high = download(high, 20);
    }
    assertLocalTrieMatchesPeer();
  }

  private void catchUp(final Map<Bytes32, Wei> balanceChanges) {
    final Map<Address, Wei> changes = new HashMap<>();
    balanceChanges.forEach(
        (accountHash, balance) -> {
          changes.put(addressByHash.get(accountHash), balance);
          peerAccounts.put(accountHash, account(balance));
        });
    final long blockNumber = pivot.getNumber() + 1;
    pivot =
        chain
            .appendCanonical(pivot, chain.balWithBalances(changes), blockNumber, peerRoot())
            .getHeader();
    applier
        .applyBlockAccessLists(blockNumber, blockNumber, accountTracker, storageTracker)
        .commit();
  }

  /** Serves and persists one account range response, returning the continuation if any. */
  private SnapV2AccountRangeRequest download(
      final SnapV2AccountRangeRequest request, final int limit) {
    final MerkleTrie<Bytes, Bytes> peerTrie = peerTrie();
    final TreeMap<Bytes32, Bytes> accounts = new TreeMap<>();
    for (final Map.Entry<Bytes32, Bytes> entry :
        peerAccounts.tailMap(request.getStartKeyHash(), true).entrySet()) {
      accounts.put(entry.getKey(), entry.getValue());
      if (entry.getKey().compareTo(request.getEndKeyHash()) >= 0 || accounts.size() >= limit) {
        break;
      }
    }
    final List<Bytes> proofs =
        new ArrayList<>(
            peerTrie.getValueWithProof(request.getStartKeyHash()).getProofRelatedNodes());
    if (!accounts.isEmpty()) {
      proofs.addAll(peerTrie.getValueWithProof(accounts.lastKey()).getProofRelatedNodes());
    }
    request.addResponse(proofProvider, accounts, proofs);
    assertThat(request.isResponseReceived()).isTrue();

    enqueued.clear();
    persistStep.persist(new StubTask(request));
    return enqueued.stream()
        .filter(SnapV2AccountRangeRequest.class::isInstance)
        .map(SnapV2AccountRangeRequest.class::cast)
        .findFirst()
        .orElse(null);
  }

  private void assertLocalTrieMatchesPeer() {
    final Bytes rootNode = coordinator.getTrieNodeUnsafe(Bytes.EMPTY).orElseThrow();
    assertThat(Hash.hash(rootNode)).isEqualTo(peerRoot());
    final MerkleTrie<Bytes, Bytes> localTrie =
        new StoredMerklePatriciaTrie<>(
            coordinator::getAccountStateTrieNode,
            Bytes32.wrap(peerRoot().getBytes()),
            Function.identity(),
            Function.identity());
    peerAccounts.forEach(
        (accountHash, value) -> assertThat(localTrie.get(accountHash)).contains(value));
  }

  private MerkleTrie<Bytes, Bytes> peerTrie() {
    final MerkleTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(Function.identity());
    peerAccounts.forEach(trie::put);
    return trie;
  }

  private Hash peerRoot() {
    return Hash.wrap(peerTrie().getRootHash());
  }

  private static int firstNibbleWithAtLeast(
      final List<Bytes32> accounts, final int fromNibble, final int count) {
    final Map<Integer, Integer> perNibble = new TreeMap<>();
    accounts.forEach(h -> perNibble.merge(firstNibble(h), 1, Integer::sum));
    return perNibble.entrySet().stream()
        .filter(e -> e.getKey() >= fromNibble && e.getValue() >= count)
        .findFirst()
        .orElseThrow()
        .getKey();
  }

  private static int firstNibble(final Bytes32 accountHash) {
    return (accountHash.get(0) & 0xff) >>> 4;
  }

  private static Bytes32 hash(final Address address) {
    return Bytes32.wrap(address.addressHash().getBytes());
  }

  private static Bytes account(final Wei balance) {
    final PmtStateTrieAccountValue value =
        new PmtStateTrieAccountValue(0L, balance, Hash.EMPTY_TRIE_HASH, Hash.EMPTY);
    return RLP.encode(value::writeTo);
  }
}
