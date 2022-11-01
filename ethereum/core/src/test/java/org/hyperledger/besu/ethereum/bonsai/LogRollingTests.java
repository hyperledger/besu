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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LogRollingTests {

  private BonsaiWorldStateArchive archive;
  private InMemoryKeyValueStorage accountStorage;
  private InMemoryKeyValueStorage codeStorage;
  private InMemoryKeyValueStorage storageStorage;
  private InMemoryKeyValueStorage trieBranchStorage;
  private InMemoryKeyValueStorage trieLogStorage;

  private BonsaiWorldStateArchive secondArchive;
  private InMemoryKeyValueStorage secondAccountStorage;
  private InMemoryKeyValueStorage secondCodeStorage;
  private InMemoryKeyValueStorage secondStorageStorage;
  private InMemoryKeyValueStorage secondTrieBranchStorage;
  private InMemoryKeyValueStorage secondTrieLogStorage;
  private final Blockchain blockchain = mock(Blockchain.class);

  private static final Address addressOne =
      Address.fromHexString("0x1111111111111111111111111111111111111111");

  private static final BlockHeader headerOne =
      new BlockHeader(
          Hash.ZERO,
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0x0ecfa454ddfe6b740f4af7b7f4c61b5c6bac2854efd2b07b27b1f53dba9bb46c"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          1,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          new MainnetBlockHeaderFunctions());
  private static final BlockHeader headerTwo =
      new BlockHeader(
          headerOne.getHash(),
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0x5b675f79cd11ba67266161d79a8d5be3ac330dfbb76300a4f15d76b610b18193"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          1,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          new MainnetBlockHeaderFunctions());

  @Before
  public void createStorage() {
    final InMemoryKeyValueStorageProvider provider = new InMemoryKeyValueStorageProvider();
    archive =
        new BonsaiWorldStateArchive(
            new TrieLogManager(blockchain, new BonsaiWorldStateKeyValueStorage(provider)),
            provider,
            blockchain);
    accountStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    codeStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    storageStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    trieBranchStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    trieLogStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);

    final InMemoryKeyValueStorageProvider secondProvider = new InMemoryKeyValueStorageProvider();
    secondArchive =
        new BonsaiWorldStateArchive(
            new TrieLogManager(blockchain, new BonsaiWorldStateKeyValueStorage(secondProvider)),
            secondProvider,
            blockchain);
    secondAccountStorage =
        (InMemoryKeyValueStorage)
            secondProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    secondCodeStorage =
        (InMemoryKeyValueStorage)
            secondProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    secondStorageStorage =
        (InMemoryKeyValueStorage)
            secondProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    secondTrieBranchStorage =
        (InMemoryKeyValueStorage)
            secondProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    secondTrieLogStorage =
        (InMemoryKeyValueStorage)
            secondProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
  }

  @Test
  public void simpleRollForwardTest() {

    final BonsaiPersistedWorldState worldState =
        new BonsaiPersistedWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(
                accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage));
    final WorldUpdater updater = worldState.updater();

    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();
    worldState.persist(headerOne);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(
                secondAccountStorage,
                secondCodeStorage,
                secondStorageStorage,
                secondTrieBranchStorage,
                secondTrieLogStorage));
    final BonsaiWorldStateUpdater secondUpdater =
        (BonsaiWorldStateUpdater) secondWorldState.updater();

    final Optional<byte[]> value = trieLogStorage.get(headerOne.getHash().toArrayUnsafe());

    final TrieLogLayer layer =
        TrieLogLayer.readFrom(new BytesValueRLPInput(Bytes.wrap(value.get()), false));

    secondUpdater.rollForward(layer);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  public void rollForwardTwice() {
    final BonsaiPersistedWorldState worldState =
        new BonsaiPersistedWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(
                accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage));

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne).getMutable();
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(
                secondAccountStorage,
                secondCodeStorage,
                secondStorageStorage,
                secondTrieBranchStorage,
                secondTrieLogStorage));
    final BonsaiWorldStateUpdater secondUpdater =
        (BonsaiWorldStateUpdater) secondWorldState.updater();

    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, headerOne.getHash());
    secondUpdater.rollForward(layerOne);
    secondUpdater.commit();
    secondWorldState.persist(null);

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());
    secondUpdater.rollForward(layerTwo);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  public void rollBackOnce() {
    final BonsaiPersistedWorldState worldState =
        new BonsaiPersistedWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(
                accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage));

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne).getMutable();
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);
    final BonsaiWorldStateUpdater firstRollbackUpdater =
        (BonsaiWorldStateUpdater) worldState.updater();

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());
    firstRollbackUpdater.rollBack(layerTwo);

    worldState.persist(headerOne);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(
                secondAccountStorage,
                secondCodeStorage,
                secondStorageStorage,
                secondTrieBranchStorage,
                secondTrieLogStorage));

    final WorldUpdater secondUpdater = secondWorldState.updater();
    final MutableAccount secondMutableAccount =
        secondUpdater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    secondMutableAccount.setCode(Bytes.of(0, 1, 2));
    secondMutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    secondUpdater.commit();

    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we don't delete the roll back log
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  private TrieLogLayer getTrieLogLayer(final InMemoryKeyValueStorage storage, final Bytes key) {
    return storage
        .get(key.toArrayUnsafe())
        .map(bytes -> TrieLogLayer.readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false)))
        .get();
  }

  private static void assertKeyValueStorageEqual(
      final KeyValueStorage first, final KeyValueStorage second) {
    final var firstKeys =
        first.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());
    final var secondKeys =
        second.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());

    assertThat(secondKeys).isEqualTo(firstKeys);
    for (final Bytes key : firstKeys) {
      assertThat(Bytes.wrap(second.get(key.toArrayUnsafe()).get()))
          .isEqualByComparingTo(Bytes.wrap(first.get(key.toArrayUnsafe()).get()));
    }
  }

  private static void assertKeyValueSubset(
      final KeyValueStorage largerSet, final KeyValueStorage smallerSet) {
    final var largerKeys =
        largerSet.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());
    final var smallerKeys =
        smallerSet.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());

    assertThat(largerKeys).containsAll(smallerKeys);
    for (final Bytes key : largerKeys) {
      if (smallerKeys.contains(key)) {
        assertThat(Bytes.wrap(largerSet.get(key.toArrayUnsafe()).get()))
            .isEqualByComparingTo(Bytes.wrap(smallerSet.get(key.toArrayUnsafe()).get()));
      }
    }
  }
}
