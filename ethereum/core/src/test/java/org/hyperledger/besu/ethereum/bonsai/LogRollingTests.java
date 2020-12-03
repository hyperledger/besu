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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;

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

  private static final Address addressOne =
      Address.fromHexString("0x1111111111111111111111111111111111111111");

  private static final Hash hashOne = Hash.hash(Bytes.of(1));
  private static final Hash hashTwo = Hash.hash(Bytes.of(2));

  @Before
  public void createStorage() {
    final InMemoryStorageProvider provider = new InMemoryStorageProvider();
    archive = new BonsaiWorldStateArchive(provider);
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

    final InMemoryStorageProvider secondProvider = new InMemoryStorageProvider();
    secondArchive = new BonsaiWorldStateArchive(secondProvider);
    secondAccountStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    secondCodeStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    secondStorageStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    secondTrieBranchStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    secondTrieLogStorage =
        (InMemoryKeyValueStorage)
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
  }

  @Test
  public void simpleRollForwardTest() {
    final BonsaiPersistedWorldState worldState =
        new BonsaiPersistedWorldState(
            archive,
            accountStorage,
            codeStorage,
            storageStorage,
            trieBranchStorage,
            trieLogStorage);
    final WorldUpdater updater = worldState.updater();

    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();
    worldState.persist(hashOne);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            secondAccountStorage,
            secondCodeStorage,
            secondStorageStorage,
            secondTrieBranchStorage,
            secondTrieLogStorage);
    final BonsaiWorldStateUpdater secondUpdater =
        (BonsaiWorldStateUpdater) secondWorldState.updater();

    final Optional<byte[]> value = trieLogStorage.get(hashOne.toArrayUnsafe());

    final TrieLogLayer layer =
        TrieLogLayer.readFrom(new BytesValueRLPInput(Bytes.wrap(value.get()), false));

    secondUpdater.rollForward(layer);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
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
            accountStorage,
            codeStorage,
            storageStorage,
            trieBranchStorage,
            trieLogStorage);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(hashOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne).getMutable();
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(hashTwo);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            secondAccountStorage,
            secondCodeStorage,
            secondStorageStorage,
            secondTrieBranchStorage,
            secondTrieLogStorage);
    final BonsaiWorldStateUpdater secondUpdater =
        (BonsaiWorldStateUpdater) secondWorldState.updater();

    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, hashOne);
    secondUpdater.rollForward(layerOne);
    secondUpdater.commit();
    secondWorldState.persist(null);

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, hashTwo);
    secondUpdater.rollForward(layerTwo);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
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
            accountStorage,
            codeStorage,
            storageStorage,
            trieBranchStorage,
            trieLogStorage);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(hashOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne).getMutable();
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(hashTwo);
    final BonsaiWorldStateUpdater firstRollbackUpdater =
        (BonsaiWorldStateUpdater) worldState.updater();

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, hashTwo);
    firstRollbackUpdater.rollBack(layerTwo);

    worldState.persist(hashTwo);

    final BonsaiPersistedWorldState secondWorldState =
        new BonsaiPersistedWorldState(
            secondArchive,
            secondAccountStorage,
            secondCodeStorage,
            secondStorageStorage,
            secondTrieBranchStorage,
            secondTrieLogStorage);

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
