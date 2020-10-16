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
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;

public class LogRollingTests {

  private InMemoryKeyValueStorage accountStorage;
  private InMemoryKeyValueStorage codeStorage;
  private InMemoryKeyValueStorage storageStorage;
  private InMemoryKeyValueStorage trieBranchStorage;
  private InMemoryKeyValueStorage trieLogStorage;

  private static final Address addressOne =
      Address.fromHexString("0x1111111111111111111111111111111111111111");
  // private static final Address addressTwo =
  //     Address.fromHexString("0x2222222222222222222222222222222222222222");

  private static final Hash hashOne = Hash.hash(Bytes.of(1));
  private static final Hash hashTwo = Hash.hash(Bytes.of(2));

  @Before
  public void createStorage() {
    accountStorage = new InMemoryKeyValueStorage();
    codeStorage = new InMemoryKeyValueStorage();
    storageStorage = new InMemoryKeyValueStorage();
    trieBranchStorage = new InMemoryKeyValueStorage();
    trieLogStorage = new InMemoryKeyValueStorage();
  }

  @Test
  public void simpleRollForwardTest() {
    final BonsaiPersistdWorldState worldState =
        new BonsaiPersistdWorldState(
            accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);
    final WorldUpdater updater = worldState.updater();

    final MutableAccount mutableAccount =
        updater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();
    worldState.persist(hashOne);

    final InMemoryKeyValueStorage newAccountStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newCodeStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newStorageStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieBranchStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieLogStorage = new InMemoryKeyValueStorage();

    final BonsaiPersistdWorldState secondWorldState =
        new BonsaiPersistdWorldState(
            newAccountStorage,
            newCodeStorage,
            newStorageStorage,
            newTrieBranchStorage,
            newTrieLogStorage);

    final Optional<byte[]> value = trieLogStorage.get(hashOne.toArrayUnsafe());

    final TrieLogLayer layer =
        TrieLogLayer.readFrom(new BytesValueRLPInput(Bytes.wrap(value.get()), false));

    secondWorldState.rollForward(layer);
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, newAccountStorage);
    assertKeyValueStorageEqual(codeStorage, newCodeStorage);
    assertKeyValueStorageEqual(storageStorage, newStorageStorage);
    assertKeyValueStorageEqual(trieBranchStorage, newTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, newTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  public void rollForwardTwice() {
    final BonsaiPersistdWorldState worldState =
        new BonsaiPersistdWorldState(
            accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);

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

    final InMemoryKeyValueStorage newAccountStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newCodeStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newStorageStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieBranchStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieLogStorage = new InMemoryKeyValueStorage();

    final BonsaiPersistdWorldState secondWorldState =
        new BonsaiPersistdWorldState(
            newAccountStorage,
            newCodeStorage,
            newStorageStorage,
            newTrieBranchStorage,
            newTrieLogStorage);

    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, hashOne);
    secondWorldState.rollForward(layerOne);
    secondWorldState.persist(null);

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, hashTwo);
    secondWorldState.rollForward(layerTwo);
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, newAccountStorage);
    assertKeyValueStorageEqual(codeStorage, newCodeStorage);
    assertKeyValueStorageEqual(storageStorage, newStorageStorage);
    assertKeyValueStorageEqual(trieBranchStorage, newTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, newTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  public void rollBackOnce() {
    final BonsaiPersistdWorldState worldState =
        new BonsaiPersistdWorldState(
            accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);

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

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, hashTwo);
    worldState.rollBack(layerTwo);

    worldState.persist(hashTwo);

    final InMemoryKeyValueStorage newAccountStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newCodeStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newStorageStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieBranchStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage newTrieLogStorage = new InMemoryKeyValueStorage();

    final BonsaiPersistdWorldState secondWorldState =
        new BonsaiPersistdWorldState(
            newAccountStorage,
            newCodeStorage,
            newStorageStorage,
            newTrieBranchStorage,
            newTrieLogStorage);

    final WorldUpdater secondUpdater = secondWorldState.updater();
    final MutableAccount secondMutableAccount =
        secondUpdater.createAccount(addressOne, 1, Wei.of(1L)).getMutable();
    secondMutableAccount.setCode(Bytes.of(0, 1, 2));
    secondMutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    secondUpdater.commit();

    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, newAccountStorage);
    assertKeyValueStorageEqual(codeStorage, newCodeStorage);
    assertKeyValueStorageEqual(storageStorage, newStorageStorage);
    assertKeyValueStorageEqual(trieBranchStorage, newTrieBranchStorage);
    // trie logs won't be the same, we don't delete the roll back log
    assertKeyValueSubset(trieLogStorage, newTrieLogStorage);
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
