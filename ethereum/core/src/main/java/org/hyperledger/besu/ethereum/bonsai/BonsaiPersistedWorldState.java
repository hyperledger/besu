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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
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
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class BonsaiPersistedWorldState implements MutableWorldState {

  private static final byte[] WORLD_ROOT_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  //  private final MutableWorldState fallbackStorage;
  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;

  private final Map<Address, BonsaiValue<BonsaiAccount>> updatedAccounts = new HashMap<>();
  private final Map<Address, BonsaiValue<Bytes>> updatedCode = new HashMap<>();
  private final Map<Address, Map<Bytes32, BonsaiValue<UInt256>>> updatedStorage = new HashMap<>();
  private final StoredMerklePatriciaTrie<Bytes32, Bytes> accountTrie;
  private Bytes32 worldStateRootHash;

  public BonsaiPersistedWorldState(
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
    var localCode = updatedCode.get(address);
    if (localCode == null) {
      return codeStorage.get(address.toArrayUnsafe()).map(Bytes::wrap).orElse(Bytes.EMPTY);
    } else {
      return localCode.getUpdated();
    }
  }

  @Override
  public void persist() {
    boolean success = false;
    final KeyValueStorageTransaction accountTx = accountStorage.startTransaction();
    final KeyValueStorageTransaction codeTx = codeStorage.startTransaction();
    final KeyValueStorageTransaction storageTx = storageStorage.startTransaction();
    final KeyValueStorageTransaction trieBranchTx = trieBranchStorage.startTransaction();
    try {

      for (final Map.Entry<Address, Map<Bytes32, BonsaiValue<UInt256>>> storageAccountUpdate :
          updatedStorage.entrySet()) {
        final Address updatedAddress = storageAccountUpdate.getKey();
        final BonsaiValue<BonsaiAccount> account = updatedAccounts.get(updatedAddress);
        final BonsaiAccount originalAccount = account.getOriginal();
        final StoredMerklePatriciaTrie<Bytes, Bytes> stateTrie =
            new StoredMerklePatriciaTrie<>(
                key -> getStorageTrieNode(updatedAddress, key),
                originalAccount == null ? Hash.EMPTY_TRIE_HASH : originalAccount.getStorageRoot(),
                Function.identity(),
                Function.identity());
        final byte[] writeAddressArray = new byte[Address.SIZE + Hash.SIZE];
        final MutableBytes writeAddressBytes = MutableBytes.wrap(writeAddressArray);
        updatedAddress.copyTo(writeAddressBytes, 0);
        for (final Map.Entry<Bytes32, BonsaiValue<UInt256>> storageUpdate :
            storageAccountUpdate.getValue().entrySet()) {
          final Bytes32 storageUpdateKey = storageUpdate.getKey();
          storageUpdateKey.copyTo(writeAddressBytes, Address.SIZE);
          final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
          if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
            storageTx.remove(writeAddressArray);
            stateTrie.remove(storageUpdateKey);
          } else {
            // TODO update state hash?
            final Bytes32 updatedStorageBytes = updatedStorage.toBytes();
            storageTx.put(writeAddressArray, updatedStorageBytes.toArrayUnsafe());
            stateTrie.put(storageUpdateKey, rlpEncode(updatedStorageBytes));
          }
        }
        // TODO prune old branches
        stateTrie.acceptAtRoot(new DumpVisitor<>(System.out));
        stateTrie.commit(
            (key, value) -> writeStorageTrieNode(trieBranchTx, updatedAddress, key, value));
        account.getUpdated().setStorageRoot(Hash.wrap(stateTrie.getRootHash()));
      }

      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate : updatedCode.entrySet()) {
        final Bytes updatedCode = codeUpdate.getValue().getUpdated();
        if (updatedCode == null) {
          codeTx.remove(codeUpdate.getKey().toArrayUnsafe());
        } else {
          codeTx.put(codeUpdate.getKey().toArrayUnsafe(), updatedCode.toArrayUnsafe());
        }
      }

      for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
          updatedAccounts.entrySet()) {
        final BonsaiAccount updatedAccount = accountUpdate.getValue().getUpdated();
        final Hash addressHash = updatedAccount.getAddressHash();
        if (updatedAccount == null) {
          accountTx.remove(accountUpdate.getKey().toArrayUnsafe());
          accountTrie.remove(addressHash);
        } else {
          final Bytes accountKey = accountUpdate.getKey();
          final Bytes accountValue = updatedAccount.serializeAccount();
          accountTx.put(accountKey.toArrayUnsafe(), accountValue.toArrayUnsafe());
          accountTrie.put(addressHash, accountValue);
        }
      }
      // TODO prune old branches
      accountTrie.commit((key, value) -> writeTrieNode(trieBranchTx, key, value));
      worldStateRootHash = accountTrie.getRootHash();
      trieBranchTx.put(WORLD_ROOT_KEY, worldStateRootHash.toArrayUnsafe());
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

  private static UInt256 convertToUInt256(final Bytes value) {
    final RLPInput in = RLP.input(value);
    return in.readUInt256Scalar();
  }

  private static Bytes rlpEncode(final Bytes bytes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytes(bytes.trimLeadingZeros());
    return out.encoded();
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
      return trieBranchStorage.get(nodeHash.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  private void writeTrieNode(
      final KeyValueStorageTransaction tx, final Bytes32 key, final Bytes value) {
    tx.put(key.toArrayUnsafe(), value.toArrayUnsafe());
  }

  private Optional<Bytes> getStorageTrieNode(final Address address, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(Bytes.concatenate(address, nodeHash).toArrayUnsafe())
          .map(Bytes::wrap);
    }
  }

  private void writeStorageTrieNode(
      final KeyValueStorageTransaction tx,
      final Address address,
      final Bytes32 key,
      final Bytes value) {
    tx.put(Bytes.concatenate(address, key).toArrayUnsafe(), value.toArrayUnsafe());
  }

  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    // TODO log read
    final Map<Bytes32, BonsaiValue<UInt256>> localAccountStorage =
        updatedStorage.computeIfAbsent(address, key -> new HashMap<>());
    final Bytes32 storageKeyBytes = Hash.hash(storageKey.toBytes());
    final BonsaiValue<UInt256> value = localAccountStorage.get(storageKeyBytes);
    if (value != null) {
      return value.getUpdated();
    }
    final byte[] key = Bytes.concatenate(address, storageKeyBytes).toArrayUnsafe();
    final Optional<byte[]> valueBits = storageStorage.get(key);
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(storageKeyBytes, ImmutableBonsaiValue.of(valueUInt, valueUInt));
      return valueUInt;
    } else {
      return UInt256.ZERO;
    }
  }

  public UInt256 getOriginalStorageValue(final Address address, final UInt256 storageKey) {
    // TODO log read?
    final Map<Bytes32, BonsaiValue<UInt256>> localAccountStorage =
        updatedStorage.computeIfAbsent(address, key -> new HashMap<>());
    final Bytes32 storageKeyBytes = storageKey.toBytes();
    final BonsaiValue<UInt256> value = localAccountStorage.get(storageKeyBytes);
    if (value != null) {
      return value.getOriginal();
    }
    final byte[] key = Bytes.concatenate(address, storageKeyBytes).toArrayUnsafe();
    final Optional<byte[]> valueBits = accountStorage.get(key);
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = convertToUInt256(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(storageKeyBytes, ImmutableBonsaiValue.of(valueUInt, valueUInt));
      return valueUInt;
    } else {
      return UInt256.ZERO;
    }
  }

  public void setStorageValue(final Address address, final UInt256 key, final UInt256 value) {
    // TODO log write
    final Bytes32 keyBytes = Hash.hash(key.toBytes());
    final Map<Bytes32, BonsaiValue<UInt256>> localAccountStorage =
        updatedStorage.computeIfAbsent(address, addr -> new HashMap<>());
    final BonsaiValue<UInt256> localValue = localAccountStorage.get(keyBytes);
    if (localValue == null) {
      final byte[] keyBits = Bytes.concatenate(address, keyBytes).toArrayUnsafe();
      final Optional<byte[]> valueBits = accountStorage.get(keyBits);
      localAccountStorage.put(
          keyBytes,
          ImmutableBonsaiValue.of(
              valueBits.map(Bytes32::wrap).map(UInt256::fromBytes).orElse(null), value));
    } else {
      localAccountStorage.put(keyBytes, localValue.revise(value));
    }
  }

  public void setCode(final Address address, final Bytes code) {
    if (updatedCode.containsKey(address)) {
      updatedCode.put(address, updatedCode.get(address).revise(code));
    } else {
      final byte[] addressBits = address.toArrayUnsafe();
      final var codeBits = codeStorage.get(addressBits);
      updatedCode.put(
          address, ImmutableBonsaiValue.of(codeBits.map(Bytes::wrap).orElse(null), code));
    }
  }

  public class BonsaiUpdater implements WorldUpdater {

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      final BonsaiAccount newAccount =
          new BonsaiAccount(
              BonsaiPersistedWorldState.this,
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
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(
                            BonsaiPersistedWorldState.this, address, Bytes.wrap(bytes), mutable))
                .orElse(null);
        updatedAccounts.put(address, ImmutableBonsaiValue.of(account, account));
        return account;
      }
    }

    @Override
    public void deleteAccount(final Address address) {
      //      System.out.printf("%n");
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
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
      //      System.out.printf("%n");
    }

    @Override
    public void commit() {
      //      System.out.printf("%n");
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
