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

import static org.hyperledger.besu.ethereum.bonsai.BonsaiAccount.fromRLP;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.trie.DumpVisitor;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class BonsaiMutableWorldState implements MutableWorldState {

  private static final byte[] WORLD_ROOT_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  //  private final MutableWorldState fallbackStorage;
  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;

  private final Map<Address, BonsaiValue<BonsaiAccount>> updatedAccounts = new HashMap<>();
  private final Map<Address, BonsaiValue<Bytes>> updatedCode = new HashMap<>();
  private final Map<Address, Map<Bytes32, BonsaiValue<Bytes32>>> updatedStorage = new HashMap<>();
  private final StoredMerklePatriciaTrie<Bytes32, Bytes> accountTrie;
  private Bytes32 worldStateRootHash;

  public BonsaiMutableWorldState(
      @SuppressWarnings("unused") final MutableWorldState fallbackStorage,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage) {
    //    this.fallbackStorage = fallbackStorage;
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    worldStateRootHash =
        Bytes32.wrap(
            trieBranchStorage.get(WORLD_ROOT_KEY).map(Bytes::wrap).orElse(Hash.EMPTY_TRIE_HASH));
    System.out.println(worldStateRootHash.toHexString());
    accountTrie =
        new StoredMerklePatriciaTrie<>(
            this::getTrieNode, worldStateRootHash, Function.identity(), Function.identity());
  }

  @Override
  public MutableWorldState copy() {
    // return null;
    throw new RuntimeException("LOL no");
  }

  public Bytes getCode(final Address address, final Hash codeHash) {
    return codeStorage
        .get(Bytes.concatenate(address, codeHash).toArray())
        .map(Bytes::wrap)
        .orElse(Bytes.EMPTY);
  }

  @Override
  public void persist() {
    boolean success = false;
    final KeyValueStorageTransaction accountTx = accountStorage.startTransaction();
    final KeyValueStorageTransaction codeTx = codeStorage.startTransaction();
    final KeyValueStorageTransaction storageTx = storageStorage.startTransaction();
    final KeyValueStorageTransaction trieBranchTx = trieBranchStorage.startTransaction();
    try {

      for (final Map.Entry<Address, Map<Bytes32, BonsaiValue<Bytes32>>> storageAccountUpdate :
          updatedStorage.entrySet()) {
        final byte[] writeAddressArray = new byte[Address.SIZE + Hash.SIZE];
        final MutableBytes writeAddressBytes = MutableBytes.wrap(writeAddressArray);
        storageAccountUpdate.getKey().copyTo(writeAddressBytes, 0);
        for (final Map.Entry<Bytes32, BonsaiValue<Bytes32>> storageUpdate :
            storageAccountUpdate.getValue().entrySet()) {
          storageUpdate.getKey().copyTo(writeAddressBytes, Address.SIZE);
          final Bytes32 updatedStorage = storageUpdate.getValue().getUpdated();
          if (updatedStorage == null) {
            storageTx.remove(writeAddressArray);
          } else {
            // TODO update state hash?
            codeTx.put(writeAddressArray, updatedStorage.toArray());
          }
        }
      }

      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate : updatedCode.entrySet()) {
        final Bytes updatedCode = codeUpdate.getValue().getUpdated();
        if (updatedCode == null) {
          codeTx.remove(codeUpdate.getKey().toArray());
        } else {
          codeTx.put(codeUpdate.getKey().toArray(), updatedCode.toArray());
        }
      }

      for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
          updatedAccounts.entrySet()) {
        final BonsaiAccount updatedAccount = accountUpdate.getValue().getUpdated();
        if (updatedAccount == null) {
          accountTx.remove(accountUpdate.getKey().toArray());
        } else {
          final Bytes accountKey = accountUpdate.getKey();
          final Bytes accountValue = updatedAccount.serializeAccount();
          final Hash addressHash = updatedAccount.getAddressHash();
          accountTx.put(accountKey.toArray(), accountValue.toArray());
          // not the most efficient, but it works
          accountTrie.remove(addressHash);
          accountTrie.put(addressHash, accountValue);
        }
      }
      // TODO prune old branches
      accountTrie.commit((key, value) -> writeTrieNode(trieBranchTx, key, value));
      worldStateRootHash = accountTrie.getRootHash();
      System.out.println(worldStateRootHash.toHexString());
      trieBranchTx.put(WORLD_ROOT_KEY, worldStateRootHash.toArray());
      success = true;
    } finally {
      if (success) {
        accountTx.commit();
        codeTx.commit();
        storageTx.commit();
        trieBranchTx.commit();
        updatedStorage.clear();
        updatedCode.clear();
        updatedAccounts.clear();
      } else {
        accountTx.rollback();
        codeTx.rollback();
        storageTx.rollback();
        trieBranchTx.rollback();
      }
    }
  }

  @Override
  public void dumpTrie(final PrintStream out) {
    accountTrie.acceptAtRoot(new DumpVisitor<>(out));
  }

  @Override
  public WorldUpdater updater() {
    return new BonsaiUpdater();
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(worldStateRootHash);
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    return null;
  }

  @Override
  public Account get(final Address address) {
    return null;
  }

  private Optional<Bytes> getTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(nodeHash.toArray()).map(Bytes::wrap);
    }
  }

  private void writeTrieNode(
      final KeyValueStorageTransaction tx, final Bytes32 key, final Bytes value) {
    tx.put(key.toArray(), value.toArray());
  }

  public class BonsaiUpdater implements WorldUpdater {

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      final BonsaiAccount newAccount =
          new BonsaiAccount(
              BonsaiMutableWorldState.this,
              address,
              Hash.hash(address),
              nonce,
              balance,
              Hash.EMPTY_TRIE_HASH,
              Hash.EMPTY,
              Account.DEFAULT_VERSION,
              true);
      updatedAccounts.put(address, ImmutableBonsaiValue.of(null, newAccount));
      return newAccount;
    }

    @Override
    public EvmAccount getOrCreate(final Address address) {
      final EvmAccount account = getAccount(address, true);
      return account == null ? createAccount(address) : account;
    }

    @Override
    public EvmAccount getAccount(final Address address) {
      return getAccount(address, true);
    }

    public EvmAccount getAccount(final Address address, final boolean mutable) {
      final BonsaiValue<BonsaiAccount> updatedAccount = updatedAccounts.get(address);
      if (updatedAccount != null) {
        return updatedAccount.getUpdated();
      } else {
        final BonsaiAccount account =
            accountStorage
                .get(address.toArray())
                .map(
                    bytes ->
                        fromRLP(BonsaiMutableWorldState.this, address, Bytes.wrap(bytes), mutable))
                .orElse(null);
        updatedAccounts.put(address, ImmutableBonsaiValue.of(account, account));
        return account;
      }
    }

    @Override
    public void deleteAccount(final Address address) {
      System.out.printf("%n");
    }

    @Override
    public Collection<Account> getTouchedAccounts() {
      return updatedAccounts.values().stream()
          .map(BonsaiValue::touched)
          .collect(Collectors.toList());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return null;
    }

    @Override
    public void revert() {
      System.out.printf("%n");
    }

    @Override
    public void commit() {
      System.out.printf("%n");
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
      return Optional.empty();
    }

    @Override
    public WorldUpdater updater() {
      return this;
    }

    @Override
    public Account get(final Address address) {
      return getAccount(address);
    }
  }
}
