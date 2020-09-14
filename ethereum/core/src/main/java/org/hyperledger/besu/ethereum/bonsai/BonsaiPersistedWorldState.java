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

import org.hyperledger.besu.ethereum.core.AbstractWorldUpdater;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.UpdateTrackingAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
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

  public Bytes getCode(final Address address, @SuppressWarnings("unused") final Hash codeHash) {
    final BonsaiValue<Bytes> localCode = updatedCode.get(address);
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
        BonsaiAccount accountUpdated = account.getUpdated();
        if (accountUpdated != null) {
          final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
              new StoredMerklePatriciaTrie<>(
                  key -> getStorageTrieNode(updatedAddress, key),
                  accountUpdated.getStorageRoot(),
                  Function.identity(),
                  Function.identity());
          final byte[] writeAddressArray = new byte[Address.SIZE + Hash.SIZE];
          final MutableBytes writeAddressBytes = MutableBytes.wrap(writeAddressArray);
          updatedAddress.copyTo(writeAddressBytes, 0);
          for (final Map.Entry<Bytes32, BonsaiValue<UInt256>> storageUpdate :
              storageAccountUpdate.getValue().entrySet()) {
            final Bytes32 storageUpdateKey = storageUpdate.getKey();
            storageUpdateKey.copyTo(writeAddressBytes, Address.SIZE);
            final Hash keyHash = Hash.hash(storageUpdateKey);
            final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
            if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
              storageTx.remove(writeAddressArray);
              storageTrie.remove(keyHash);
            } else {
              // TODO update state hash?
              final Bytes32 updatedStorageBytes = updatedStorage.toBytes();
              storageTx.put(writeAddressArray, updatedStorageBytes.toArrayUnsafe());
              storageTrie.put(keyHash, rlpEncode(updatedStorageBytes));
            }
          }
          // TODO prune old branches
          //        System.out.println("Storage Trie Dump - " + storageAccountUpdate.getKey());
          //        storageTrie.acceptAtRoot(new DumpVisitor<>(System.out));
          storageTrie.commit(
              (key, value) -> writeStorageTrieNode(trieBranchTx, updatedAddress, key, value));
          accountUpdated.setStorageRoot(Hash.wrap(storageTrie.getRootHash()));
        }
      }

      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate : updatedCode.entrySet()) {
        final Bytes updatedCode = codeUpdate.getValue().getUpdated();
        if (updatedCode == null || updatedCode.size() == 0) {
          codeTx.remove(codeUpdate.getKey().toArrayUnsafe());
        } else {
          codeTx.put(codeUpdate.getKey().toArrayUnsafe(), updatedCode.toArrayUnsafe());
        }
      }

      for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
          updatedAccounts.entrySet()) {
        final BonsaiAccount updatedAccount = accountUpdate.getValue().getUpdated();
        final Bytes accountKey = accountUpdate.getKey();
        if (updatedAccount == null) {
          final Hash addressHash = Hash.hash(accountKey);
          accountTx.remove(accountKey.toArrayUnsafe());
          accountTrie.remove(addressHash);
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
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
    //    System.out.println("World State Trie Dump");
    //    accountTrie.acceptAtRoot(new DumpVisitor<>(out));
  }

  @Override
  public WorldUpdater updater() {
    return new BonsaiUpdater(this);
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(worldStateRootHash);
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("NIY");
  }

  @Override
  public Account get(final Address address) {
    final BonsaiValue<BonsaiAccount> lookup = updatedAccounts.get(address);
    if (lookup != null) {
      return lookup.getUpdated();
    } else {
      return accountStorage
          .get(address.toArrayUnsafe())
          .map(bytes -> fromRLP(BonsaiPersistedWorldState.this, address, Bytes.wrap(bytes), true))
          .orElse(null);
    }
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
    final Bytes32 storageKeyBytes = storageKey.toBytes();
    final BonsaiValue<UInt256> value = localAccountStorage.get(storageKeyBytes);
    if (value != null) {
      return value.getUpdated();
    }
    final byte[] key = Bytes.concatenate(address, storageKeyBytes).toArrayUnsafe();
    final Optional<byte[]> valueBits = storageStorage.get(key);
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(storageKeyBytes, new BonsaiValue<>(valueUInt, valueUInt));
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
      localAccountStorage.put(storageKeyBytes, new BonsaiValue<>(valueUInt, valueUInt));
      return valueUInt;
    } else {
      return UInt256.ZERO;
    }
  }

  public void setStorageValue(final Address address, final UInt256 key, final UInt256 value) {
    // TODO log write
    final Bytes32 keyBytes = Hash.hash(key.toBytes());
    final Map<Bytes32, BonsaiValue<UInt256>> localAccountStorage =
        updatedStorage.computeIfAbsent(address, __ -> new HashMap<>());
    final BonsaiValue<UInt256> localValue = localAccountStorage.get(keyBytes);
    if (localValue == null) {
      final byte[] keyBits = Bytes.concatenate(address, keyBytes).toArrayUnsafe();
      final Optional<byte[]> valueBits = accountStorage.get(keyBits);
      localAccountStorage.put(
          keyBytes,
          new BonsaiValue<>(
              valueBits.map(Bytes32::wrap).map(UInt256::fromBytes).orElse(null), value));
    } else {
      localValue.setUpdated(value);
    }
  }

  public void setCode(final Address address, final Bytes code) {
    if (updatedCode.containsKey(address)) {
      updatedCode.get(address).setUpdated(code);
    } else {
      final byte[] addressBits = address.toArrayUnsafe();
      final Optional<byte[]> codeBits = codeStorage.get(addressBits);
      updatedCode.put(address, new BonsaiValue<>(codeBits.map(Bytes::wrap).orElse(null), code));
    }
  }

  public class BonsaiUpdater
      extends AbstractWorldUpdater<BonsaiPersistedWorldState, BonsaiAccount> {

    protected BonsaiUpdater(final BonsaiPersistedWorldState world) {
      super(world);
    }

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
      updatedAccounts.put(address, new BonsaiValue<>(null, newAccount));
      return newAccount;
    }

    @Override
    protected BonsaiAccount getForMutation(final Address address) {
      final BonsaiValue<BonsaiAccount> lookup = updatedAccounts.get(address);
      BonsaiAccount acct = null;
      if (lookup != null) {
        acct = lookup.getUpdated();
      }
      if (acct == null) {
        acct =
            accountStorage
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(BonsaiPersistedWorldState.this, address, Bytes.wrap(bytes), true))
                .orElse(null);
        if (acct != null) {
          updatedAccounts.put(
              address,
              new BonsaiValue<>(new BonsaiAccount(BonsaiPersistedWorldState.this, acct), acct));
        }
      }
      return acct;
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      // FIXME ?
      return updatedAccounts();
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      // FIXME ?
      return deletedAccounts();
    }

    @Override
    public void revert() {
      // FIXME ?
      deletedAccounts().clear();
      updatedAccounts().clear();
    }

    @Override
    public void commit() {
      for (final Address address : deletedAccounts()) {
        final BonsaiValue<BonsaiAccount> updatedAccount = updatedAccounts.get(address);
        if (updatedAccount != null) {
          updatedAccount.setUpdated(null);
        }
      }

      for (final UpdateTrackingAccount<BonsaiAccount> tracked : updatedAccounts()) {
        BonsaiAccount updatedAccount = tracked.getWrappedAccount();
        if (updatedAccount == null) {
          updatedAccount = new BonsaiAccount(BonsaiPersistedWorldState.this, tracked);
        } else {
          updatedAccount.setBalance(tracked.getBalance());
          updatedAccount.setNonce(tracked.getNonce());
          updatedAccount.setCode(tracked.getCode());
          if (tracked.getStorageWasCleared()) {
            updatedAccount.clearStorage();
          }
          tracked.getUpdatedStorage().forEach(updatedAccount::setStorageValue);
        }
        final Address updatedAddress = updatedAccount.getAddress();

        final BonsaiValue<BonsaiAccount> pendingAccount =
            updatedAccounts.computeIfAbsent(updatedAddress, __ -> new BonsaiValue<>(null, null));
        pendingAccount.setUpdated(updatedAccount);

        final BonsaiValue<Bytes> pendingCode =
            updatedCode.computeIfAbsent(updatedAddress, __ -> new BonsaiValue<>(null, null));
        pendingCode.setUpdated(updatedAccount.getCode());

        final Map<Bytes32, BonsaiValue<UInt256>> pendingStorageUpdates =
            updatedStorage.computeIfAbsent(updatedAddress, __ -> new HashMap<>());
        if (tracked.getStorageWasCleared()) {
          pendingStorageUpdates.clear();
        }

        for (final Entry<UInt256, UInt256> storageUpdate :
            updatedAccount.getUpdatedStorage().entrySet()) {
          final Bytes32 key = storageUpdate.getKey().toBytes();
          final UInt256 value = storageUpdate.getValue();
          final BonsaiValue<UInt256> pendingValue = pendingStorageUpdates.get(key);
          if (pendingValue == null) {
            // if it existed before it would have been loaded, so it's a new create
            pendingStorageUpdates.put(key, new BonsaiValue<>(null, value));
          } else {
            pendingValue.setUpdated(value);
          }
        }

        // TODO address preimage
      }
    }
  }
}
