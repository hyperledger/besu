/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldState;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.AccountStorageEntry;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldState.StreamableAccount;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

// TODO: make that an abstract mutable world state test, and create sub-class for all world state
// implementations.
public class DefaultMutableWorldStateTest {
  // The following test cases are loosely derived from the testTransactionToItself
  // GeneralStateReferenceTest.

  private static final Address ADDRESS =
      Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

  private static MutableWorldState createEmpty(final WorldStateKeyValueStorage storage) {
    final WorldStatePreimageKeyValueStorage preimageStorage =
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
    return new DefaultMutableWorldState(storage, preimageStorage);
  }

  private static MutableWorldState createEmpty() {
    return createInMemoryWorldState();
  }

  @Test
  public void rootHash_Empty() {
    final MutableWorldState worldState = createEmpty();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    worldState.persist();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());
  }

  @Test
  public void containsAccount_AccountDoesNotExist() {
    final WorldState worldState = createEmpty();
    assertNull(worldState.get(ADDRESS));
  }

  @Test
  public void containsAccount_AccountExists() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.commit();
    assertNotNull(worldState.get(ADDRESS));
    assertEquals(
        Hash.fromHexString("0xa3e1c133a5a51b03399ed9ad0380f3182e9e18322f232b816dd4b9094f871e1b"),
        worldState.rootHash());
  }

  @Test
  public void removeAccount_AccountDoesNotExist() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.deleteAccount(ADDRESS);
    updater.commit();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    worldState.persist();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());
  }

  @Test
  public void removeAccount_UpdatedAccount() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.deleteAccount(ADDRESS);
    updater.commit();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    worldState.persist();
    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());
  }

  @Test
  public void removeAccount_AccountExists() {
    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.commit();
    assertNotNull(worldState.get(ADDRESS));
    assertNotEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    // Delete account
    updater = worldState.updater();
    updater.deleteAccount(ADDRESS);
    assertNull(updater.get(ADDRESS));
    assertNull(updater.getMutable(ADDRESS));
    updater.commit();
    assertNull(updater.get(ADDRESS));

    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());
  }

  @Test
  public void removeAccount_AccountExistsAndIsPersisted() {
    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.commit();
    worldState.persist();
    assertNotNull(worldState.get(ADDRESS));
    assertNotEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    // Delete account
    updater = worldState.updater();
    updater.deleteAccount(ADDRESS);
    assertNull(updater.get(ADDRESS));
    assertNull(updater.getMutable(ADDRESS));
    // Check account is gone after committing
    updater.commit();
    assertNull(updater.get(ADDRESS));
    // And after persisting
    worldState.persist();
    assertNull(updater.get(ADDRESS));

    assertEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());
  }

  @Test
  public void streamAccounts_empty() {
    final MutableWorldState worldState = createEmpty();
    final Stream<StreamableAccount> accounts = worldState.streamAccounts(Bytes32.ZERO, 10);
    assertThat(accounts.count()).isEqualTo(0L);
  }

  @Test
  public void streamAccounts_singleAccount() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.commit();

    List<StreamableAccount> accounts =
        worldState.streamAccounts(Bytes32.ZERO, 10).collect(Collectors.toList());
    assertThat(accounts.size()).isEqualTo(1L);
    assertThat(accounts.get(0).getAddress()).hasValue(ADDRESS);
    assertThat(accounts.get(0).getBalance()).isEqualTo(Wei.of(100000));

    // Check again after persisting
    worldState.persist();
    accounts = worldState.streamAccounts(Bytes32.ZERO, 10).collect(Collectors.toList());
    assertThat(accounts.size()).isEqualTo(1L);
    assertThat(accounts.get(0).getAddress()).hasValue(ADDRESS);
    assertThat(accounts.get(0).getBalance()).isEqualTo(Wei.of(100000));
  }

  @Test
  public void streamAccounts_multipleAccounts() {
    final Address addr1 = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    final Address addr2 = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0c");

    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();

    // Create an account
    final MutableAccount accountA = updater.createAccount(addr1);
    accountA.setBalance(Wei.of(100000));
    // Create another
    final MutableAccount accountB = updater.createAccount(addr2);
    accountB.setNonce(1);
    // Commit changes
    updater.commit();

    final boolean accountAIsFirst =
        accountA.getAddressHash().compareTo(accountB.getAddressHash()) < 0;
    final Hash startHash = accountAIsFirst ? accountA.getAddressHash() : accountB.getAddressHash();

    // Get first account
    final List<StreamableAccount> firstAccount =
        worldState.streamAccounts(startHash, 1).collect(Collectors.toList());
    assertThat(firstAccount.size()).isEqualTo(1L);
    assertThat(firstAccount.get(0).getAddress())
        .hasValue(accountAIsFirst ? accountA.getAddress() : accountB.getAddress());

    // Get both accounts
    final List<StreamableAccount> allAccounts =
        worldState.streamAccounts(Bytes32.ZERO, 2).collect(Collectors.toList());
    assertThat(allAccounts.size()).isEqualTo(2L);
    assertThat(allAccounts.get(0).getAddress())
        .hasValue(accountAIsFirst ? accountA.getAddress() : accountB.getAddress());
    assertThat(allAccounts.get(1).getAddress())
        .hasValue(accountAIsFirst ? accountB.getAddress() : accountA.getAddress());

    // Get second account
    final Bytes32 startHashForSecondAccount = startHash.asUInt256().plus(1L).getBytes();
    final List<StreamableAccount> secondAccount =
        worldState.streamAccounts(startHashForSecondAccount, 100).collect(Collectors.toList());
    assertThat(secondAccount.size()).isEqualTo(1L);
    assertThat(secondAccount.get(0).getAddress())
        .hasValue(accountAIsFirst ? accountB.getAddress() : accountA.getAddress());
  }

  @Test
  public void commitAndPersist() {
    final KeyValueStorage storage = new InMemoryKeyValueStorage();
    final WorldStateKeyValueStorage kvWorldStateStorage = new WorldStateKeyValueStorage(storage);
    final MutableWorldState worldState = createEmpty(kvWorldStateStorage);
    final WorldUpdater updater = worldState.updater();
    final Wei newBalance = Wei.of(100000);
    final Hash expectedRootHash =
        Hash.fromHexString("0xa3e1c133a5a51b03399ed9ad0380f3182e9e18322f232b816dd4b9094f871e1b");

    // Update account and assert we get the expected response from updater
    updater.createAccount(ADDRESS).setBalance(newBalance);
    assertNotNull(updater.get(ADDRESS));
    assertEquals(newBalance, updater.get(ADDRESS).getBalance());

    // Commit and check assertions
    updater.commit();
    assertEquals(expectedRootHash, worldState.rootHash());
    assertNotNull(worldState.get(ADDRESS));
    assertEquals(newBalance, worldState.get(ADDRESS).getBalance());

    // Check that storage is empty before persisting
    assertThat(kvWorldStateStorage.isWorldStateAvailable(worldState.rootHash())).isFalse();

    // Persist and re-run assertions
    worldState.persist();

    assertThat(kvWorldStateStorage.isWorldStateAvailable(worldState.rootHash())).isTrue();
    assertEquals(expectedRootHash, worldState.rootHash());
    assertNotNull(worldState.get(ADDRESS));
    assertEquals(newBalance, worldState.get(ADDRESS).getBalance());

    // Create new world state and check that it can access modified address
    final MutableWorldState newWorldState =
        new DefaultMutableWorldState(
            expectedRootHash,
            new WorldStateKeyValueStorage(storage),
            new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
    assertEquals(expectedRootHash, newWorldState.rootHash());
    assertNotNull(newWorldState.get(ADDRESS));
    assertEquals(newBalance, newWorldState.get(ADDRESS).getBalance());
  }

  @Test
  public void getAccountNonce_AccountExists() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setNonce(1L);
    updater.commit();
    assertEquals(1L, worldState.get(ADDRESS).getNonce());
    assertEquals(
        Hash.fromHexString("0x9648b05cc2eef5513ae2edfe16bfcedb3d1c60ffb5dff3fc501bd3e4ae39f536"),
        worldState.rootHash());
  }

  @Test
  public void replaceAccountNonce() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setNonce(1L);
    account.setNonce(2L);
    updater.commit();
    assertEquals(2L, worldState.get(ADDRESS).getNonce());
    assertEquals(
        Hash.fromHexString("0x7f64d13e61301a5154a5f06483a38572629e977b316cbe5a28b5f0522010a4bf"),
        worldState.rootHash());
  }

  @Test
  public void getAccountBalance_AccountExists() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(ADDRESS).setBalance(Wei.of(100000));
    updater.commit();
    assertEquals(Wei.of(100000), worldState.get(ADDRESS).getBalance());
  }

  @Test
  public void replaceAccountBalance() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setBalance(Wei.of(200000));
    updater.commit();
    assertEquals(Wei.of(200000), worldState.get(ADDRESS).getBalance());
    assertEquals(
        Hash.fromHexString("0xbfa4e0598cc2b810a8ccc4a2d9a4c575574d05c9c4a7f915e6b8545953a5051e"),
        worldState.rootHash());
  }

  @Test
  public void setStorageValue_ZeroValue() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(UInt256.ZERO, UInt256.ZERO);
    updater.commit();
    assertEquals(UInt256.ZERO, worldState.get(ADDRESS).getStorageValue(UInt256.ZERO));
    assertEquals(
        Hash.fromHexString("0xa3e1c133a5a51b03399ed9ad0380f3182e9e18322f232b816dd4b9094f871e1b"),
        worldState.rootHash());
  }

  @Test
  public void setStorageValue_NonzeroValue() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(UInt256.ONE, UInt256.of(2));
    updater.commit();
    assertEquals(UInt256.of(2), worldState.get(ADDRESS).getStorageValue(UInt256.ONE));
    assertEquals(
        Hash.fromHexString("0xd31ce0bf3bf8790083a8ebde418244fda3b1cca952d7119ed244f86d03044656"),
        worldState.rootHash());
  }

  @Test
  public void replaceStorageValue_NonzeroValue() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(UInt256.ONE, UInt256.of(2));
    account.setStorageValue(UInt256.ONE, UInt256.of(3));
    updater.commit();
    assertEquals(UInt256.of(3), worldState.get(ADDRESS).getStorageValue(UInt256.ONE));
    assertEquals(
        Hash.fromHexString("0x1d0ddb5079fe5b8689124b68c9e5bb3f4d8e13c2f7489d24f088c78fd45e058d"),
        worldState.rootHash());
  }

  @Test
  public void replaceStorageValue_ZeroValue() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(UInt256.ONE, UInt256.of(2));
    account.setStorageValue(UInt256.ONE, UInt256.ZERO);
    updater.commit();
    assertEquals(
        Hash.fromHexString("0xa3e1c133a5a51b03399ed9ad0380f3182e9e18322f232b816dd4b9094f871e1b"),
        worldState.rootHash());
  }

  @Test
  public void getOriginalStorageValue() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater setupUpdater = worldState.updater();
    final MutableAccount setupAccount = setupUpdater.createAccount(ADDRESS);
    setupAccount.setStorageValue(UInt256.ONE, UInt256.of(2));
    setupUpdater.commit();

    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.getOrCreate(ADDRESS);
    assertThat(account.getOriginalStorageValue(UInt256.ONE)).isEqualTo(UInt256.of(2));

    account.setStorageValue(UInt256.ONE, UInt256.of(3));
    assertThat(account.getOriginalStorageValue(UInt256.ONE)).isEqualTo(UInt256.of(2));
  }

  @Test
  public void originalStorageValueIsAlwaysZeroIfStorageWasCleared() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater setupUpdater = worldState.updater();
    final MutableAccount setupAccount = setupUpdater.createAccount(ADDRESS);
    setupAccount.setStorageValue(UInt256.ONE, UInt256.of(2));
    setupUpdater.commit();

    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.getOrCreate(ADDRESS);

    account.clearStorage();
    assertThat(account.getOriginalStorageValue(UInt256.ONE)).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void clearStorage() {
    final UInt256 storageKey = UInt256.of(1L);
    final UInt256 storageValue = UInt256.of(2L);

    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(storageKey, storageValue);
    assertThat(account.getStorageValue(storageKey)).isEqualTo(storageValue);

    // Clear storage
    account = updater.getMutable(ADDRESS);
    assertThat(account).isNotNull();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(storageValue);
    account.clearStorage();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);

    // Check storage is cleared after committing
    updater.commit();
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);

    // And after persisting
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void clearStorage_AfterPersisting() {
    final UInt256 storageKey = UInt256.of(1L);
    final UInt256 storageValue = UInt256.of(2L);

    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(storageKey, storageValue);
    updater.commit();
    worldState.persist();
    assertNotNull(worldState.get(ADDRESS));
    assertNotEquals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, worldState.rootHash());

    // Clear storage
    account = updater.getMutable(ADDRESS);
    assertThat(account).isNotNull();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(storageValue);
    account.clearStorage();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(storageValue);

    // Check storage is cleared after committing
    updater.commit();
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);

    // And after persisting
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void clearStorageThenEdit() {
    final UInt256 storageKey = UInt256.of(1L);
    final UInt256 originalStorageValue = UInt256.of(2L);
    final UInt256 newStorageValue = UInt256.of(3L);

    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(storageKey, originalStorageValue);
    assertThat(account.getStorageValue(storageKey)).isEqualTo(originalStorageValue);

    // Clear storage then edit
    account = updater.getMutable(ADDRESS);
    assertThat(account).isNotNull();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(originalStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(originalStorageValue);
    account.clearStorage();
    account.setStorageValue(storageKey, newStorageValue);
    assertThat(account.getStorageValue(storageKey)).isEqualTo(newStorageValue);

    // Check storage is cleared after committing
    updater.commit();
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);

    // And after persisting
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
  }

  @Test
  public void clearStorageThenEditAfterPersisting() {
    final UInt256 storageKey = UInt256.of(1L);
    final UInt256 originalStorageValue = UInt256.of(2L);
    final UInt256 newStorageValue = UInt256.of(3L);

    // Create a world state with one account
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(storageKey, originalStorageValue);
    assertThat(account.getStorageValue(storageKey)).isEqualTo(originalStorageValue);
    updater.commit();
    worldState.persist();

    // Clear storage then edit
    account = updater.getMutable(ADDRESS);
    assertThat(account).isNotNull();
    assertThat(account.getStorageValue(storageKey)).isEqualTo(originalStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(originalStorageValue);
    account.clearStorage();
    account.setStorageValue(storageKey, newStorageValue);
    assertThat(account.getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(originalStorageValue);

    // Check storage is cleared after committing
    updater.commit();
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);

    // And after persisting
    assertThat(updater.getMutable(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(updater.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
    assertThat(worldState.get(ADDRESS).getStorageValue(storageKey)).isEqualTo(newStorageValue);
  }

  @Test
  public void replaceAccountCode() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater = worldState.updater();
    final MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setCode(BytesValue.of(1, 2, 3));
    account.setVersion(Account.DEFAULT_VERSION);
    account.setCode(BytesValue.of(3, 2, 1));
    updater.commit();
    assertEquals(BytesValue.of(3, 2, 1), worldState.get(ADDRESS).getCode());
    assertEquals(Account.DEFAULT_VERSION, worldState.get(ADDRESS).getVersion());
    assertEquals(
        Hash.fromHexString("0xc14f5e30581de9155ea092affa665fad83bcd9f98e45c4a42885b9b36d939702"),
        worldState.rootHash());
  }

  @Test
  public void revert() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater1 = worldState.updater();
    final MutableAccount account1 = updater1.createAccount(ADDRESS);
    account1.setBalance(Wei.of(200000));
    updater1.commit();

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount account2 = updater2.getMutable(ADDRESS);
    account2.setBalance(Wei.of(300000));
    assertEquals(Wei.of(300000), updater2.get(ADDRESS).getBalance());

    updater2.revert();
    assertEquals(Wei.of(200000), updater2.get(ADDRESS).getBalance());

    updater2.commit();
    assertEquals(Wei.of(200000), worldState.get(ADDRESS).getBalance());

    assertEquals(
        Hash.fromHexString("0xbfa4e0598cc2b810a8ccc4a2d9a4c575574d05c9c4a7f915e6b8545953a5051e"),
        worldState.rootHash());
  }

  @Test
  public void shouldReturnNullForGetMutableWhenAccountDeletedInAncestor() {
    final MutableWorldState worldState = createEmpty();
    final WorldUpdater updater1 = worldState.updater();
    final MutableAccount account1 = updater1.createAccount(ADDRESS);
    updater1.commit();
    assertThat(updater1.get(ADDRESS))
        .isEqualToComparingOnlyGivenFields(account1, "address", "nonce", "balance", "codeHash");
    updater1.deleteAccount(ADDRESS);

    final WorldUpdater updater2 = updater1.updater();
    assertThat(updater2.get(ADDRESS)).isEqualTo(null);

    final WorldUpdater updater3 = updater2.updater();
    assertThat(updater3.getMutable(ADDRESS)).isEqualTo(null);
  }

  @Test
  public void shouldCombineUnchangedAndChangedValuesWhenRetrievingStorageEntries() {
    final MutableWorldState worldState = createEmpty();
    WorldUpdater updater = worldState.updater();
    MutableAccount account = updater.createAccount(ADDRESS);
    account.setBalance(Wei.of(100000));
    account.setStorageValue(UInt256.ONE, UInt256.of(2));
    account.setStorageValue(UInt256.of(2), UInt256.of(5));
    updater.commit();

    final List<AccountStorageEntry> initialSetOfEntries = new ArrayList<>();
    initialSetOfEntries.add(AccountStorageEntry.forKeyAndValue(UInt256.ONE, UInt256.of(2)));
    initialSetOfEntries.add(AccountStorageEntry.forKeyAndValue(UInt256.of(2), UInt256.of(5)));
    final Map<Bytes32, AccountStorageEntry> initialEntries = new TreeMap<>();
    initialSetOfEntries.forEach(entry -> initialEntries.put(entry.getKeyHash(), entry));

    updater = worldState.updater();
    account = updater.getMutable(ADDRESS);
    account.setStorageValue(UInt256.ONE, UInt256.of(3));
    account.setStorageValue(UInt256.of(3), UInt256.of(6));

    final List<AccountStorageEntry> finalSetOfEntries = new ArrayList<>();
    finalSetOfEntries.add(AccountStorageEntry.forKeyAndValue(UInt256.ONE, UInt256.of(3)));
    finalSetOfEntries.add(AccountStorageEntry.forKeyAndValue(UInt256.of(2), UInt256.of(5)));
    finalSetOfEntries.add(AccountStorageEntry.forKeyAndValue(UInt256.of(3), UInt256.of(6)));
    final Map<Bytes32, AccountStorageEntry> finalEntries = new TreeMap<>();
    finalSetOfEntries.forEach(entry -> finalEntries.put(entry.getKeyHash(), entry));

    assertThat(account.storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(finalEntries);
    assertThat(updater.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(finalEntries);
    assertThat(worldState.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(initialEntries);

    worldState.persist();
    assertThat(updater.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(finalEntries);
    assertThat(worldState.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(initialEntries);

    updater.commit();
    assertThat(worldState.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(finalEntries);

    worldState.persist();
    assertThat(worldState.get(ADDRESS).storageEntriesFrom(Hash.ZERO, 10)).isEqualTo(finalEntries);
  }
}
