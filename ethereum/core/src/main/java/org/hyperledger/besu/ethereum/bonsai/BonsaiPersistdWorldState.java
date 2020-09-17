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
import org.hyperledger.besu.ethereum.trie.DumpVisitor;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
// FIXME speling
public class BonsaiPersistdWorldState implements MutableWorldState {
  private static final Logger LOG = LogManager.getLogger();

  private static final byte[] WORLD_ROOT_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  //  private final MutableWorldState fallbackStorage;
  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;

  private final Map<Address, BonsaiAccount> accountsToUpdate = new HashMap<>();
  private final Set<Address> accountsToDelete = new HashSet<>();
  private final Map<Address, BonsaiValue<Bytes>> codeToUpdate = new HashMap<>();
  private final Set<Address> storageToClear = new HashSet<>();
  private final Map<Address, Map<Bytes32, BonsaiValue<UInt256>>> storageToUpdate = new HashMap<>();
  private final StoredMerklePatriciaTrie<Bytes32, Bytes> accountTrie;
  private Bytes32 worldStateRootHash;

  public BonsaiPersistdWorldState(
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
    final BonsaiValue<Bytes> localCode = codeToUpdate.get(address);
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

      for (final Address address : storageToClear) {
        LOG.trace(" clearing - {}", address.toHexString());
        // because we are clearing persisted values we need the account root as persisted
        final BonsaiAccount oldAccount = (BonsaiAccount) accountStorage
            .get(address.toArrayUnsafe())
            .map(bytes -> fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
            .orElse(null);
        if (oldAccount == null) {
          // This is when an account is both created and deleted within the scope of the same
          // transaction.  A not-uncommon DeFi bot pattern.
          continue;
        }
        final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
            new StoredMerklePatriciaTrie<>(
                key -> getStorageTrieNode(address, key),
                oldAccount.getStorageRoot(),
                Function.identity(),
                Function.identity());
        var entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
        while (!entriesToDelete.isEmpty()) {
          entriesToDelete.keySet().forEach(k -> storageTx.remove(Bytes.concatenate(address, k).toArrayUnsafe()));
          if (entriesToDelete.size() == 256) {
            entriesToDelete.keySet().forEach(storageTrie::remove);
            entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
          } else {
            break;
          }
        }
      }

      for (final Map.Entry<Address, Map<Bytes32, BonsaiValue<UInt256>>> storageAccountUpdate :
          storageToUpdate.entrySet()) {
        final Address updatedAddress = storageAccountUpdate.getKey();
        final BonsaiAccount accountUpdated = accountsToUpdate.get(updatedAddress);
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
            Hash.hash(storageUpdateKey).copyTo(writeAddressBytes, Address.SIZE);
            final Hash keyHash = Hash.hash(storageUpdateKey);
            final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
            if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
              LOG.trace(" - write {} -> delete", writeAddressBytes.toHexString());
              storageTx.remove(writeAddressArray);
              storageTrie.remove(keyHash);
            } else {
              // TODO update state hash?
              final Bytes32 updatedStorageBytes = updatedStorage.toBytes();
              LOG.trace(" - write {} -> {}", writeAddressBytes.toHexString(), updatedStorageBytes.toShortHexString());
              storageTx.put(writeAddressArray, updatedStorageBytes.toArrayUnsafe());
              storageTrie.put(keyHash, rlpEncode(updatedStorageBytes));
            }
          }
          // TODO prune old branches
          //        System.out.println("Storage Trie Dump - " + storageAccountUpdate.getKey());
          //        storageTrie.acceptAtRoot(new
          //        DumpVisitor<>(System.out));
          storageTrie.commit(
              (key, value) -> writeStorageTrieNode(trieBranchTx, updatedAddress, key, value));
          accountUpdated.setStorageRoot(Hash.wrap(storageTrie.getRootHash()));
        }
      }

      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate : codeToUpdate.entrySet()) {
        final Bytes updatedCode = codeUpdate.getValue().getUpdated();
        if (updatedCode == null || updatedCode.size() == 0) {
          codeTx.remove(codeUpdate.getKey().toArrayUnsafe());
        } else {
          codeTx.put(codeUpdate.getKey().toArrayUnsafe(), updatedCode.toArrayUnsafe());
        }
      }

      for (final Address accountKey : accountsToDelete) {
        final Hash addressHash = Hash.hash(accountKey);
        accountTx.remove(accountKey.toArrayUnsafe());
        accountTrie.remove(addressHash);
        // TODO clear storage
      }

      for (final Map.Entry<Address, BonsaiAccount> accountUpdate : accountsToUpdate.entrySet()) {
        final BonsaiAccount updatedAccount = accountUpdate.getValue();
        final Bytes accountKey = accountUpdate.getKey();
        final Hash addressHash = updatedAccount.getAddressHash();
        final Bytes accountValue = updatedAccount.serializeAccount();
        accountTx.put(accountKey.toArrayUnsafe(), accountValue.toArrayUnsafe());
        accountTrie.put(addressHash, accountValue);
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
        storageToClear.clear();
        storageToUpdate.clear();
        codeToUpdate.clear();
        accountsToUpdate.clear();
        accountsToDelete.clear();
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
    System.out.println("World State Trie Dump");
    accountTrie.acceptAtRoot(new DumpVisitor<>());
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
    final BonsaiAccount account = accountsToUpdate.get(address);
    if (account != null) {
      return account;
    } else if (accountsToDelete.contains(address)) {
      return null;
    } else {
      return accountStorage
          .get(address.toArrayUnsafe())
          .map(bytes -> fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
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
        storageToUpdate.computeIfAbsent(address, key -> new HashMap<>());
    final Bytes32 storageKeyBytes = storageKey.toBytes();
    final BonsaiValue<UInt256> value = localAccountStorage.get(storageKeyBytes);
    if (value != null) {
      return value.getUpdated();
    }
    final Bytes compositeKey = Bytes.concatenate(address, Hash.hash(storageKeyBytes));
    final Optional<byte[]> valueBits = storageStorage.get(compositeKey.toArrayUnsafe());
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(storageKeyBytes, new BonsaiValue<>(valueUInt, valueUInt));
      LOG.trace(" - read {} -> {}", compositeKey.toHexString(), valueUInt.toShortHexString());
      return valueUInt;
    } else {
      LOG.trace(" - read {} -> empty", compositeKey.toHexString());
      return UInt256.ZERO;
    }
  }

  public UInt256 getOriginalStorageValue(final Address address, final UInt256 storageKey) {
    // TODO log read?
    final Map<Bytes32, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.computeIfAbsent(address, key -> new HashMap<>());
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
        storageToUpdate.computeIfAbsent(address, __ -> new HashMap<>());
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
    if (codeToUpdate.containsKey(address)) {
      codeToUpdate.get(address).setUpdated(code);
    } else {
      final byte[] addressBits = address.toArrayUnsafe();
      final Optional<byte[]> codeBits = codeStorage.get(addressBits);
      codeToUpdate.put(address, new BonsaiValue<>(codeBits.map(Bytes::wrap).orElse(null), code));
    }
  }

  public class BonsaiUpdater extends AbstractWorldUpdater<BonsaiPersistdWorldState, BonsaiAccount> {

    protected BonsaiUpdater(final BonsaiPersistdWorldState world) {
      super(world);
    }

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      final BonsaiAccount newAccount =
          new BonsaiAccount(
              BonsaiPersistdWorldState.this,
              address,
              Hash.hash(address),
              nonce,
              balance,
              Hash.EMPTY_TRIE_HASH,
              Hash.EMPTY,
              Account.DEFAULT_VERSION,
              true);
      accountsToUpdate.put(address, newAccount);
      return newAccount;
    }

    @Override
    protected BonsaiAccount getForMutation(final Address address) {
      BonsaiAccount acct = accountsToUpdate.get(address);
      if (acct == null && !accountsToDelete.contains(address)) {
        acct =
            accountStorage
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
                .orElse(null);
        if (acct != null) {
          accountsToUpdate.put(address, acct);
          //              new BonsaiAccount(BonsaiPersistedWorldState.this, acct));
        }
      }
      return acct;
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      // FIXME ?
      return getUpdatedAccounts();
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      // FIXME ?
      return getDeletedAccounts();
    }

    @Override
    public void revert() {
      LOG.trace("REVERT!");
      // FIXME ?
      getDeletedAccounts().clear();
      getUpdatedAccounts().clear();
    }

    @Override
    public void commit() {
      LOG.trace("deleted accounts");
      for (final Address deletedAddress : getDeletedAccounts()) {
        LOG.trace("{} - {}", deletedAddress, Hash.hash(deletedAddress));
        codeToUpdate.put(deletedAddress, new BonsaiValue<>(null, Bytes.EMPTY));
        storageToClear.add(deletedAddress);
        accountsToUpdate.remove(deletedAddress);
        accountsToDelete.add(deletedAddress);
        // TODO delete storage trie
      }

      LOG.trace("updated accounts");
      for (final UpdateTrackingAccount<BonsaiAccount> tracked : getUpdatedAccounts()) {
        LOG.trace("{} - {}", tracked.getAddress(), tracked.getAddressHash());
        BonsaiAccount updatedAccount = tracked.getWrappedAccount();
        if (updatedAccount == null) {
          updatedAccount = new BonsaiAccount(BonsaiPersistdWorldState.this, tracked);
          accountsToUpdate.put(tracked.getAddress(), updatedAccount);
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

        final BonsaiValue<Bytes> pendingCode =
            codeToUpdate.computeIfAbsent(updatedAddress, __ -> new BonsaiValue<>(null, null));
        pendingCode.setUpdated(updatedAccount.getCode());

        final Map<Bytes32, BonsaiValue<UInt256>> pendingStorageUpdates =
            storageToUpdate.computeIfAbsent(updatedAddress, __ -> new HashMap<>());
        if (tracked.getStorageWasCleared()) {
          LOG.trace(" - Storage Cleared");
          // TODO mark that we need to clear out an accounts storage
          storageToClear.add(tracked.getAddress());
          pendingStorageUpdates.clear();
        }

        final var entries =
            new TreeSet<>(
                Comparator.comparing((Function<Entry<UInt256, UInt256>, UInt256>) Entry::getKey));
        entries.addAll(updatedAccount.getUpdatedStorage().entrySet());

        for (final Entry<UInt256, UInt256> storageUpdate : entries) {
          final Bytes32 key = storageUpdate.getKey().toBytes();
          final UInt256 value = storageUpdate.getValue();
          final BonsaiValue<UInt256> pendingValue = pendingStorageUpdates.get(key);
          if (pendingValue == null) {
            LOG.trace(" {} : {} -> {}", key.toShortHexString(), "", value.toShortHexString());
            // if it existed before it would have been loaded, so it's a new create
            pendingStorageUpdates.put(key, new BonsaiValue<>(null, value));
          } else {
            final var original = pendingValue.getOriginal();
            LOG.trace(
                " {} : {} -> {}",
                key.toShortHexString(),
                original == null ? "" : original.toShortHexString(),
                value.toShortHexString());
            pendingValue.setUpdated(value);
          }
        }
        updatedAccount.getUpdatedStorage().clear();

        // TODO address preimage
      }
    }
  }
}
