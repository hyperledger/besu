/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.trie.diffbased.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.StorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

public class BonsaiWorldStateKeyValueStorageTest {

  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{FlatDbMode.FULL}, {FlatDbMode.PARTIAL}});
  }

  final BonsaiWorldStateKeyValueStorage storage = emptyStorage();

  public void setUp(final FlatDbMode flatDbMode) {
    if (flatDbMode.equals(FlatDbMode.FULL)) {
      storage.upgradeToFullFlatDbMode();
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  void getCode_returnsEmpty(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    assertThat(storage.getCode(Hash.EMPTY, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStateTrieNode_returnsEmptyNode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStorageTrieNode_returnsEmptyNode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    assertThat(
            storage.getAccountStorageTrieNode(
                Hash.EMPTY, Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getCode_saveAndGetSpecialValues(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    storage
        .updater()
        .putCode(Hash.EMPTY, MerkleTrie.EMPTY_TRIE_NODE)
        .putCode(Hash.EMPTY, Bytes.EMPTY)
        .commit();

    assertThat(storage.getCode(Hash.hash(MerkleTrie.EMPTY_TRIE_NODE), Hash.EMPTY))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getCode_saveAndGetRegularValue(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    final Bytes bytes = Bytes.fromHexString("0x123456");
    storage.updater().putCode(Hash.EMPTY, bytes).commit();

    assertThat(storage.getCode(Hash.hash(bytes), Hash.EMPTY)).contains(bytes);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStateTrieNode_saveAndGetSpecialValues(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    storage
        .updater()
        .putAccountStateTrieNode(
            Bytes.EMPTY, Hash.hash(MerkleTrie.EMPTY_TRIE_NODE), MerkleTrie.EMPTY_TRIE_NODE)
        .putAccountStateTrieNode(Bytes.EMPTY, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStateTrieNode_saveAndGetRegularValue(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    final Bytes location = Bytes.fromHexString("0x01");
    final Bytes bytes = Bytes.fromHexString("0x123456");

    storage.updater().putAccountStateTrieNode(location, Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(location, Hash.hash(bytes))).contains(bytes);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStorageTrieNode_saveAndGetSpecialValues(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);

    storage
        .updater()
        .putAccountStorageTrieNode(
            Hash.EMPTY,
            Bytes.EMPTY,
            Hash.hash(MerkleTrie.EMPTY_TRIE_NODE),
            MerkleTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(Hash.EMPTY, Bytes.EMPTY, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(
            storage.getAccountStorageTrieNode(
                Hash.EMPTY, Bytes.EMPTY, Hash.hash(MerkleTrie.EMPTY_TRIE_NODE)))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStorageTrieNode(Hash.EMPTY, Bytes.EMPTY, Hash.EMPTY))
        .contains(Bytes.EMPTY);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccountStorageTrieNode_saveAndGetRegularValue(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    final Hash accountHash = Address.fromHexString("0x1").addressHash();
    final Bytes location = Bytes.fromHexString("0x01");
    final Bytes bytes = Bytes.fromHexString("0x123456");

    storage
        .updater()
        .putAccountStorageTrieNode(accountHash, location, Hash.hash(bytes), bytes)
        .commit();

    assertThat(storage.getAccountStorageTrieNode(accountHash, location, Hash.hash(bytes)))
        .contains(bytes);
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccount_notLoadFromTrieWhenEmptyAndFlatDbFullMode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    Assumptions.assumeTrue(flatDbMode == FlatDbMode.FULL);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());
    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final MerkleTrie<Bytes, Bytes> trie = TrieGenerator.generateTrie(coordinator, 1);

    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(root -> StorageEntriesCollector.collectEntries(root, Hash.ZERO, 1));

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, trie.getRootHash().toArrayUnsafe());
    updater.commit();

    // remove flat database
    storage.clearFlatDatabase();

    storage.upgradeToFullFlatDbMode();

    Mockito.reset(storage);

    assertThat(storage.getAccount(Hash.wrap(accounts.firstKey()))).isEmpty();

    verify(storage, times(0)).getAccountStateTrieNode(any(), eq(trie.getRootHash()));
  }

  @ParameterizedTest
  @MethodSource("data")
  void getAccount_loadFromTrieWhenEmptyAndFlatDbPartialMode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    Assumptions.assumeTrue(flatDbMode == FlatDbMode.PARTIAL);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());
    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final MerkleTrie<Bytes, Bytes> trie = TrieGenerator.generateTrie(coordinator, 1);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(root -> StorageEntriesCollector.collectEntries(root, Hash.ZERO, 1));

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, trie.getRootHash().toArrayUnsafe());
    updater.commit();

    // remove flat database
    storage.clearFlatDatabase();

    Mockito.reset(storage);

    assertThat(storage.getAccount(Hash.wrap(accounts.firstKey())))
        .contains(accounts.firstEntry().getValue());

    verify(storage, times(1)).getAccountStateTrieNode(any(), eq(trie.getRootHash()));
  }

  @ParameterizedTest
  @MethodSource("data")
  void shouldUsePartialDBStrategyAfterDowngradingMode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    Assumptions.assumeTrue(flatDbMode == FlatDbMode.PARTIAL);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());
    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final MerkleTrie<Bytes, Bytes> trie = TrieGenerator.generateTrie(coordinator, 1);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(root -> StorageEntriesCollector.collectEntries(root, Hash.ZERO, 1));

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, trie.getRootHash().toArrayUnsafe());
    updater.commit();

    Mockito.reset(storage);

    // remove flat database
    storage.clearFlatDatabase();

    storage.upgradeToFullFlatDbMode();
    assertThat(storage.getAccount(Hash.wrap(accounts.firstKey()))).isEmpty();

    storage.downgradeToPartialFlatDbMode();
    assertThat(storage.getAccount(Hash.wrap(accounts.firstKey())))
        .contains(accounts.firstEntry().getValue());
  }

  @ParameterizedTest
  @MethodSource("data")
  void getStorage_loadFromTrieWhenEmptyWithPartialMode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    Assumptions.assumeTrue(flatDbMode == FlatDbMode.PARTIAL);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());
    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final MerkleTrie<Bytes, Bytes> trie = TrieGenerator.generateTrie(coordinator, 1);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(root -> StorageEntriesCollector.collectEntries(root, Hash.ZERO, 1));

    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accounts.firstEntry().getValue()));

    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                storage.getAccountStorageTrieNode(Hash.wrap(accounts.firstKey()), location, hash),
            stateTrieAccountValue.getStorageRoot(),
            b -> b,
            b -> b);

    final TreeMap<Bytes32, Bytes> slots =
        (TreeMap<Bytes32, Bytes>)
            storageTrie.entriesFrom(
                root -> StorageEntriesCollector.collectEntries(root, Hash.ZERO, 1));

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, trie.getRootHash().toArrayUnsafe());
    updater.commit();

    // remove flat database
    storage.clearFlatDatabase();

    assertThat(
            storage.getStorageValueByStorageSlotKey(
                Hash.wrap(accounts.firstKey()),
                new StorageSlotKey(Hash.wrap(slots.firstKey()), Optional.empty())))
        .map(Bytes::toShortHexString)
        .contains(slots.firstEntry().getValue().toShortHexString());

    verify(storage, times(2))
        .getAccountStorageTrieNode(
            eq(Hash.wrap(accounts.firstKey())), any(), eq(storageTrie.getRootHash()));
  }

  @ParameterizedTest
  @MethodSource("data")
  void getStorage_loadFromTrieWhenEmptyWithFullMode(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    Assumptions.assumeTrue(flatDbMode == FlatDbMode.FULL);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());
    storage.upgradeToFullFlatDbMode();
    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final MerkleTrie<Bytes, Bytes> trie = TrieGenerator.generateTrie(coordinator, 1);

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, trie.getRootHash().toArrayUnsafe());
    updater.commit();

    // remove flat database
    storage.clearFlatDatabase();
  }

  @ParameterizedTest
  @MethodSource("data")
  void clear_reloadFlatDbStrategy(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    final BonsaiWorldStateKeyValueStorage storage = spy(emptyStorage());

    // save world state root hash
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater.putAccountInfoState(Hash.ZERO, Bytes32.random()).commit();

    assertThat(storage.getAccount(Hash.ZERO)).isNotEmpty();

    // clear
    storage.clear();

    assertThat(storage.getFlatDbStrategy()).isNotNull();

    assertThat(storage.getAccount(Hash.ZERO)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("data")
  void reconcilesNonConflictingUpdaters(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    final Hash accountHashA = Address.fromHexString("0x1").addressHash();
    final Hash accountHashB = Address.fromHexString("0x2").addressHash();
    final Hash accountHashD = Address.fromHexString("0x4").addressHash();
    final Bytes bytesA = Bytes.fromHexString("0x12");
    final Bytes bytesB = Bytes.fromHexString("0x1234");
    final Bytes bytesC = Bytes.fromHexString("0x123456");

    final BonsaiWorldStateKeyValueStorage.Updater updaterA = storage.updater();
    final BonsaiWorldStateKeyValueStorage.Updater updaterB = storage.updater();

    updaterA.putCode(accountHashA, bytesA);
    updaterB.putCode(accountHashB, bytesA);
    updaterB.putCode(accountHashB, bytesB);
    updaterA.putCode(accountHashD, bytesC);

    updaterA.commit();
    updaterB.commit();

    assertThat(storage.getCode(Hash.hash(bytesB), accountHashB)).contains(bytesB);
    assertThat(storage.getCode(Hash.hash(bytesC), accountHashD)).contains(bytesC);
  }

  @ParameterizedTest
  @MethodSource("data")
  void isWorldStateAvailable_defaultIsFalse(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);
    assertThat(emptyStorage().isWorldStateAvailable(UInt256.valueOf(1), Hash.EMPTY)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("data")
  void isWorldStateAvailable_StateAvailableByRootHash(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);

    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    final Bytes rootHashKey = Bytes32.fromHexString("0x01");
    updater
        .getWorldStateTransaction()
        .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, rootHashKey.toArrayUnsafe());
    updater.commit();
    assertThat(storage.isWorldStateAvailable(Hash.wrap(Bytes32.wrap(rootHashKey)), Hash.EMPTY))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("data")
  void isWorldStateAvailable_afterCallingSaveWorldstate(final FlatDbMode flatDbMode) {
    setUp(flatDbMode);

    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();

    final Bytes blockHash = Bytes32.fromHexString("0x01");
    final Bytes32 nodeHashKey = Bytes32.fromHexString("0x02");
    final Bytes nodeValue = Bytes32.fromHexString("0x03");

    assertThat(storage.isWorldStateAvailable(Bytes32.wrap(nodeHashKey), Hash.EMPTY)).isFalse();

    updater.saveWorldState(blockHash, nodeHashKey, nodeValue);
    updater.commit();

    assertThat(storage.isWorldStateAvailable(Bytes32.wrap(nodeHashKey), Hash.EMPTY)).isTrue();
  }

  private BonsaiWorldStateKeyValueStorage emptyStorage() {
    return new BonsaiWorldStateKeyValueStorage(
        new InMemoryKeyValueStorageProvider(),
        new NoOpMetricsSystem(),
        DataStorageConfiguration.DEFAULT_CONFIG);
  }

  @Test
  void successfulPruneReturnsTrue() {
    final KeyValueStorage mockTrieLogStorage = mock(KeyValueStorage.class);
    when(mockTrieLogStorage.tryDelete(any())).thenReturn(true);
    final BonsaiWorldStateKeyValueStorage storage = setupMockStorage(mockTrieLogStorage);
    assertThat(storage.pruneTrieLog(Hash.ZERO)).isTrue();
  }

  @Test
  void failedPruneReturnsFalse() {
    final KeyValueStorage mockTrieLogStorage = mock(KeyValueStorage.class);
    when(mockTrieLogStorage.tryDelete(any())).thenReturn(false);
    final BonsaiWorldStateKeyValueStorage storage = setupMockStorage(mockTrieLogStorage);
    assertThat(storage.pruneTrieLog(Hash.ZERO)).isFalse();
  }

  @Test
  void exceptionalPruneReturnsFalse() {
    final KeyValueStorage mockTrieLogStorage = mock(KeyValueStorage.class);
    when(mockTrieLogStorage.tryDelete(any())).thenThrow(new RuntimeException("test exception"));
    final BonsaiWorldStateKeyValueStorage storage = setupMockStorage(mockTrieLogStorage);
    assertThat(storage.pruneTrieLog(Hash.ZERO)).isFalse();
  }

  private BonsaiWorldStateKeyValueStorage setupMockStorage(
      final KeyValueStorage mockTrieLogStorage) {
    final StorageProvider mockStorageProvider = mock(StorageProvider.class);
    when(mockStorageProvider.getStorageBySegmentIdentifier(
            KeyValueSegmentIdentifier.TRIE_LOG_STORAGE))
        .thenReturn(mockTrieLogStorage);
    when(mockStorageProvider.getStorageBySegmentIdentifiers(any()))
        .thenReturn(mock(SegmentedKeyValueStorage.class));
    return new BonsaiWorldStateKeyValueStorage(
        mockStorageProvider, new NoOpMetricsSystem(), DataStorageConfiguration.DEFAULT_CONFIG);
  }
}
