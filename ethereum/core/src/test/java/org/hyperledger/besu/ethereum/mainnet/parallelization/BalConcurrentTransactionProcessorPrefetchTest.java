/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig.createStatefulConfigWithTrie;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ImmutableBalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.NoOpBonsaiWorldStateRegistry;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiMerkleTriePreLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedSnapshotWorldStateStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BalConcurrentTransactionProcessorPrefetchTest {

  private BonsaiWorldStateKeyValueStorage parentStorage;
  private BonsaiCachedWorldStateStorage baseCachedStorage;
  private BlockHeader blockHeader;

  private static final Executor SYNC_EXECUTOR = Runnable::run;

  enum StorageMode {
    CACHED,
    SNAPSHOT
  }

  @BeforeEach
  public void setup() {
    parentStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

    baseCachedStorage =
        new BonsaiCachedWorldStateStorage(
            parentStorage, 1000, 1000, 1000, 1000, new NoOpMetricsSystem());

    blockHeader = mock(BlockHeader.class);
    when(blockHeader.getParentHash()).thenReturn(Hash.ZERO);
  }

  static Stream<Arguments> storageModeProvider() {
    return Stream.of(Arguments.of(StorageMode.CACHED), Arguments.of(StorageMode.SNAPSHOT));
  }

  private BonsaiWorldStateKeyValueStorage createStorage(final StorageMode mode) {
    if (mode == StorageMode.CACHED) {
      return baseCachedStorage;
    } else {
      // Create snapshot but return it directly (it uses the same cache manager)
      return baseCachedStorage.createSnapshot();
    }
  }

  private BonsaiWorldState createWorldState(final BonsaiWorldStateKeyValueStorage storage) {
    return new BonsaiWorldState(
        storage,
        new NoopBonsaiMerkleTriePreLoader(),
        new NoOpBonsaiWorldStateRegistry(storage, EvmConfiguration.DEFAULT, new CodeCache()),
        new NoOpTrieLogManager(),
        EvmConfiguration.DEFAULT,
        createStatefulConfigWithTrie(),
        new CodeCache());
  }

  private ProtocolContext createProtocolContext(final BonsaiWorldState worldState) {
    ProtocolContext protocolContext = mock(ProtocolContext.class);
    MutableBlockchain blockchain = mock(MutableBlockchain.class);
    BlockHeader chainHeadBlockHeader = mock(BlockHeader.class);
    WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);
    when(chainHeadBlockHeader.getStateRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);
    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(worldStateArchive.getWorldState(any())).thenReturn(Optional.of(worldState));

    return protocolContext;
  }

  private Bytes bytesFromInt(final int value) {
    return Bytes.wrap(ByteBuffer.allocate(4).putInt(value).array());
  }

  @ParameterizedTest
  @MethodSource("storageModeProvider")
  public void testPrefetch_loadsAccountsIntoCache(final StorageMode mode) {
    Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");

    Bytes accountData1 = Bytes.of(1, 2, 3);
    Bytes accountData2 = Bytes.of(4, 5, 6);

    BonsaiWorldStateKeyValueStorage.Updater updater = parentStorage.updater();
    updater.putAccountInfoState(address1.addressHash(), accountData1);
    updater.putAccountInfoState(address2.addressHash(), accountData2);
    updater.commit();

    BonsaiWorldStateKeyValueStorage storage = createStorage(mode);
    BonsaiWorldState worldState = createWorldState(storage);
    ProtocolContext protocolContext = createProtocolContext(worldState);

    // Verify storage type
    if (mode == StorageMode.CACHED) {
      assertThat(storage).isInstanceOf(BonsaiCachedWorldStateStorage.class);
      assertThat(storage).isNotInstanceOf(BonsaiCachedSnapshotWorldStateStorage.class);
    } else {
      assertThat(storage).isInstanceOf(BonsaiCachedSnapshotWorldStateStorage.class);
    }

    List<BlockAccessList.AccountChanges> accountChangesList = new ArrayList<>();
    accountChangesList.add(
        new BlockAccessList.AccountChanges(
            address1, List.of(), List.of(), List.of(), List.of(), List.of()));
    accountChangesList.add(
        new BlockAccessList.AccountChanges(
            address2, List.of(), List.of(), List.of(), List.of(), List.of()));

    BlockAccessList blockAccessList = new BlockAccessList(accountChangesList);

    BalConfiguration config =
        ImmutableBalConfiguration.builder().balProcessingTimeout(Duration.ofSeconds(10)).build();

    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor =
        new BalConcurrentTransactionProcessor(txProcessor, blockAccessList, config);

    long initialCacheSize = baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE);

    processor.preFetchRead(protocolContext, blockHeader, SYNC_EXECUTOR, SYNC_EXECUTOR).join();

    // Verify cache was populated on the base cached storage
    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE))
        .isGreaterThanOrEqualTo(initialCacheSize + 2);
    assertThat(
            baseCachedStorage.isCached(
                ACCOUNT_INFO_STATE, address1.addressHash().getBytes().toArrayUnsafe()))
        .isTrue();
    assertThat(
            baseCachedStorage.isCached(
                ACCOUNT_INFO_STATE, address2.addressHash().getBytes().toArrayUnsafe()))
        .isTrue();

    // Verify cached values match expected data
    assertThat(
            baseCachedStorage.getCachedValue(
                ACCOUNT_INFO_STATE, address1.addressHash().getBytes().toArrayUnsafe()))
        .isPresent()
        .get()
        .satisfies(
            versionedValue -> {
              assertThat(versionedValue.isRemoval()).isFalse();
              assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(accountData1);
            });

    assertThat(
            baseCachedStorage.getCachedValue(
                ACCOUNT_INFO_STATE, address2.addressHash().getBytes().toArrayUnsafe()))
        .isPresent()
        .get()
        .satisfies(
            versionedValue -> {
              assertThat(versionedValue.isRemoval()).isFalse();
              assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(accountData2);
            });

    worldState.close();
  }

  @ParameterizedTest
  @MethodSource("storageModeProvider")
  public void testPrefetch_loadsStorageSlotsIntoCache(final StorageMode mode) {
    Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    StorageSlotKey slot1 = new StorageSlotKey(UInt256.valueOf(1));
    StorageSlotKey slot2 = new StorageSlotKey(UInt256.valueOf(2));

    Bytes accountData = Bytes.of(1, 2, 3);
    Bytes storageValue1 = Bytes.of(10);
    Bytes storageValue2 = Bytes.of(20);

    BonsaiWorldStateKeyValueStorage.Updater updater = parentStorage.updater();
    updater.putAccountInfoState(address.addressHash(), accountData);
    updater.putStorageValueBySlotHash(address.addressHash(), slot1.getSlotHash(), storageValue1);
    updater.putStorageValueBySlotHash(address.addressHash(), slot2.getSlotHash(), storageValue2);
    updater.commit();

    BonsaiWorldStateKeyValueStorage storage = createStorage(mode);
    BonsaiWorldState worldState = createWorldState(storage);
    ProtocolContext protocolContext = createProtocolContext(worldState);

    List<BlockAccessList.SlotChanges> storageChangesList = new ArrayList<>();
    storageChangesList.add(new BlockAccessList.SlotChanges(slot1, List.of()));
    storageChangesList.add(new BlockAccessList.SlotChanges(slot2, List.of()));

    List<BlockAccessList.AccountChanges> accountChangesList = new ArrayList<>();
    accountChangesList.add(
        new BlockAccessList.AccountChanges(
            address, storageChangesList, List.of(), List.of(), List.of(), List.of()));

    BlockAccessList blockAccessList = new BlockAccessList(accountChangesList);

    BalConfiguration config =
        ImmutableBalConfiguration.builder().balProcessingTimeout(Duration.ofSeconds(10)).build();

    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor =
        new BalConcurrentTransactionProcessor(txProcessor, blockAccessList, config);

    long initialStorageCacheSize = baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE);

    processor.preFetchRead(protocolContext, blockHeader, SYNC_EXECUTOR, SYNC_EXECUTOR).join();

    assertThat(
            baseCachedStorage.isCached(
                ACCOUNT_INFO_STATE, address.addressHash().getBytes().toArrayUnsafe()))
        .isTrue();
    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE))
        .isGreaterThanOrEqualTo(initialStorageCacheSize + 2);

    // Verify account value
    assertThat(
            baseCachedStorage.getCachedValue(
                ACCOUNT_INFO_STATE, address.addressHash().getBytes().toArrayUnsafe()))
        .isPresent()
        .get()
        .satisfies(
            versionedValue -> {
              assertThat(versionedValue.isRemoval()).isFalse();
              assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(accountData);
            });

    // Verify storage slot values
    byte[] storageKey1 =
        Bytes.concatenate(address.addressHash().getBytes(), slot1.getSlotHash().getBytes())
            .toArrayUnsafe();
    byte[] storageKey2 =
        Bytes.concatenate(address.addressHash().getBytes(), slot2.getSlotHash().getBytes())
            .toArrayUnsafe();

    assertThat(baseCachedStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, storageKey1))
        .isPresent()
        .get()
        .satisfies(
            versionedValue -> {
              assertThat(versionedValue.isRemoval()).isFalse();
              assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(storageValue1);
            });

    assertThat(baseCachedStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, storageKey2))
        .isPresent()
        .get()
        .satisfies(
            versionedValue -> {
              assertThat(versionedValue.isRemoval()).isFalse();
              assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(storageValue2);
            });

    worldState.close();
  }

  @ParameterizedTest
  @MethodSource("storageModeProvider")
  public void testPrefetch_multipleAccountsAndSlots(final StorageMode mode) {
    List<BlockAccessList.AccountChanges> allChanges = new ArrayList<>();
    Map<Address, Bytes> expectedAccountData = new HashMap<>();
    Map<BonsaiCachedWorldStateStorage.ByteArrayWrapper, Bytes> expectedStorageData =
        new HashMap<>(); // Use ByteArrayWrapper as key

    for (int i = 0; i < 5; i++) {
      Address address = Address.fromHexString(String.format("0x%040d", i + 1));
      Bytes accountData = bytesFromInt(i);
      expectedAccountData.put(address, accountData);

      BonsaiWorldStateKeyValueStorage.Updater accountUpdater = parentStorage.updater();
      accountUpdater.putAccountInfoState(address.addressHash(), accountData);
      accountUpdater.commit();

      List<BlockAccessList.SlotChanges> storageChangesList = new ArrayList<>();
      for (int j = 0; j < 3; j++) {
        StorageSlotKey slot = new StorageSlotKey(UInt256.valueOf(i * 10 + j));
        Bytes storageValue = bytesFromInt(i * 10 + j);

        // Use the same cache key for both storing and retrieving
        byte[] cacheKey =
            Bytes.concatenate(address.addressHash().getBytes(), slot.getSlotHash().getBytes())
                .toArrayUnsafe();
        expectedStorageData.put(
            new BonsaiCachedWorldStateStorage.ByteArrayWrapper(cacheKey), storageValue);

        BonsaiWorldStateKeyValueStorage.Updater storageUpdater = parentStorage.updater();
        storageUpdater.putStorageValueBySlotHash(
            address.addressHash(), slot.getSlotHash(), storageValue);
        storageUpdater.commit();

        storageChangesList.add(new BlockAccessList.SlotChanges(slot, List.of()));
      }

      allChanges.add(
          new BlockAccessList.AccountChanges(
              address, storageChangesList, List.of(), List.of(), List.of(), List.of()));
    }

    BonsaiWorldStateKeyValueStorage storage = createStorage(mode);
    BonsaiWorldState worldState = createWorldState(storage);
    ProtocolContext protocolContext = createProtocolContext(worldState);

    BlockAccessList blockAccessList = new BlockAccessList(allChanges);

    BalConfiguration config =
        ImmutableBalConfiguration.builder().balProcessingTimeout(Duration.ofSeconds(10)).build();

    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor =
        new BalConcurrentTransactionProcessor(txProcessor, blockAccessList, config);

    long initialAccountCache = baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE);
    long initialStorageCache = baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE);

    processor.preFetchRead(protocolContext, blockHeader, SYNC_EXECUTOR, SYNC_EXECUTOR).join();

    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE))
        .isGreaterThanOrEqualTo(initialAccountCache + 5);
    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE))
        .isGreaterThanOrEqualTo(initialStorageCache + 15);

    // Verify all account values
    for (Map.Entry<Address, Bytes> entry : expectedAccountData.entrySet()) {
      Address address = entry.getKey();
      Bytes expectedData = entry.getValue();

      assertThat(
              baseCachedStorage.getCachedValue(
                  ACCOUNT_INFO_STATE, address.addressHash().getBytes().toArrayUnsafe()))
          .isPresent()
          .get()
          .satisfies(
              versionedValue -> {
                assertThat(versionedValue.isRemoval()).isFalse();
                assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(expectedData);
              });
    }

    // Verify all storage values using the same cache keys
    for (Map.Entry<BonsaiCachedWorldStateStorage.ByteArrayWrapper, Bytes> entry :
        expectedStorageData.entrySet()) {
      byte[] cacheKey = entry.getKey().getData();
      Bytes expectedValue = entry.getValue();

      assertThat(baseCachedStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, cacheKey))
          .isPresent()
          .get()
          .satisfies(
              versionedValue -> {
                assertThat(versionedValue.isRemoval()).isFalse();
                assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(expectedValue);
              });
    }

    worldState.close();
  }

  @ParameterizedTest
  @MethodSource("storageModeProvider")
  public void testPrefetch_largeNumberOfAccountsAndSlots(final StorageMode mode) {
    final int numAccounts = 10;
    final int slotsPerAccount = 3;

    List<BlockAccessList.AccountChanges> accountChangesList = new ArrayList<>();
    Map<Address, Bytes> expectedAccountData = new HashMap<>();
    Map<BonsaiCachedWorldStateStorage.ByteArrayWrapper, Bytes> expectedStorageData =
        new HashMap<>();

    for (int i = 0; i < numAccounts; i++) {
      Address address = Address.fromHexString(String.format("0x%040d", i + 1));
      Bytes accountData = bytesFromInt(i);
      expectedAccountData.put(address, accountData);

      BonsaiWorldStateKeyValueStorage.Updater accountUpdater = parentStorage.updater();
      accountUpdater.putAccountInfoState(address.addressHash(), accountData);
      accountUpdater.commit();

      List<BlockAccessList.SlotChanges> storageChangesList = new ArrayList<>();
      for (int j = 0; j < slotsPerAccount; j++) {
        StorageSlotKey slot = new StorageSlotKey(UInt256.valueOf(i * 100 + j));
        Bytes storageValue = bytesFromInt(i * 100 + j);

        byte[] cacheKey =
            Bytes.concatenate(address.addressHash().getBytes(), slot.getSlotHash().getBytes())
                .toArrayUnsafe();
        expectedStorageData.put(
            new BonsaiCachedWorldStateStorage.ByteArrayWrapper(cacheKey), storageValue);

        BonsaiWorldStateKeyValueStorage.Updater storageUpdater = parentStorage.updater();
        storageUpdater.putStorageValueBySlotHash(
            address.addressHash(), slot.getSlotHash(), storageValue);
        storageUpdater.commit();

        storageChangesList.add(new BlockAccessList.SlotChanges(slot, List.of()));
      }

      accountChangesList.add(
          new BlockAccessList.AccountChanges(
              address, storageChangesList, List.of(), List.of(), List.of(), List.of()));
    }

    BonsaiWorldStateKeyValueStorage storage = createStorage(mode);
    BonsaiWorldState worldState = createWorldState(storage);
    ProtocolContext protocolContext = createProtocolContext(worldState);

    BlockAccessList blockAccessList = new BlockAccessList(accountChangesList);

    BalConfiguration config =
        ImmutableBalConfiguration.builder().balProcessingTimeout(Duration.ofSeconds(10)).build();

    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor =
        new BalConcurrentTransactionProcessor(txProcessor, blockAccessList, config);

    long initialAccountCache = baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE);
    long initialStorageCache = baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE);

    long startTime = System.currentTimeMillis();
    processor.preFetchRead(protocolContext, blockHeader, SYNC_EXECUTOR, SYNC_EXECUTOR).join();
    long endTime = System.currentTimeMillis();

    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_INFO_STATE))
        .isGreaterThanOrEqualTo(initialAccountCache + numAccounts);
    assertThat(baseCachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE))
        .isGreaterThanOrEqualTo(initialStorageCache + (numAccounts * slotsPerAccount));
    assertThat(endTime - startTime).isLessThan(5000);

    // Verify all account values
    for (Map.Entry<Address, Bytes> entry : expectedAccountData.entrySet()) {
      Address address = entry.getKey();
      Bytes expectedData = entry.getValue();

      assertThat(
              baseCachedStorage.getCachedValue(
                  ACCOUNT_INFO_STATE, address.addressHash().getBytes().toArrayUnsafe()))
          .isPresent()
          .get()
          .satisfies(
              versionedValue -> {
                assertThat(versionedValue.isRemoval()).isFalse();
                assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(expectedData);
              });
    }

    // Verify all storage values
    for (Map.Entry<BonsaiCachedWorldStateStorage.ByteArrayWrapper, Bytes> entry :
        expectedStorageData.entrySet()) {
      byte[] cacheKey = entry.getKey().getData();
      Bytes expectedValue = entry.getValue();

      assertThat(baseCachedStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, cacheKey))
          .isPresent()
          .get()
          .satisfies(
              versionedValue -> {
                assertThat(versionedValue.isRemoval()).isFalse();
                assertThat(Bytes.wrap(versionedValue.getValue())).isEqualTo(expectedValue);
              });
    }

    worldState.close();
  }
}
