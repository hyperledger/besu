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

import tech.pegasys.pantheon.ethereum.core.AbstractWorldUpdater;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.AccountStorageEntry;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.trie.StoredMerklePatriciaTrie;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

public class DefaultMutableWorldState implements MutableWorldState {

  private final WorldStateStorage worldStateStorage;
  private final WorldStatePreimageStorage preimageStorage;

  private final MerklePatriciaTrie<Bytes32, BytesValue> accountStateTrie;
  private final Map<Address, MerklePatriciaTrie<Bytes32, BytesValue>> updatedStorageTries =
      new HashMap<>();
  private final Map<Address, BytesValue> updatedAccountCode = new HashMap<>();
  private final Map<Bytes32, UInt256> newStorageKeyPreimages = new HashMap<>();

  public DefaultMutableWorldState(
      final WorldStateStorage storage, final WorldStatePreimageStorage preimageStorage) {
    this(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH, storage, preimageStorage);
  }

  public DefaultMutableWorldState(
      final Bytes32 rootHash,
      final WorldStateStorage worldStateStorage,
      final WorldStatePreimageStorage preimageStorage) {
    this.worldStateStorage = worldStateStorage;
    this.accountStateTrie = newAccountStateTrie(rootHash);
    this.preimageStorage = preimageStorage;
  }

  public DefaultMutableWorldState(final WorldState worldState) {
    // TODO: this is an abstraction leak (and kind of incorrect in that we reuse the underlying
    // storage), but the reason for this is that the accounts() method is unimplemented below and
    // can't be until NC-754.
    if (!(worldState instanceof DefaultMutableWorldState)) {
      throw new UnsupportedOperationException();
    }

    final DefaultMutableWorldState other = (DefaultMutableWorldState) worldState;
    this.worldStateStorage = other.worldStateStorage;
    this.preimageStorage = other.preimageStorage;
    this.accountStateTrie = newAccountStateTrie(other.accountStateTrie.getRootHash());
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> newAccountStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, rootHash, b -> b, b -> b);
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> newAccountStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStorageTrieNode, rootHash, b -> b, b -> b);
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(accountStateTrie.getRootHash());
  }

  @Override
  public MutableWorldState copy() {
    return new DefaultMutableWorldState(rootHash(), worldStateStorage, preimageStorage);
  }

  @Override
  public Account get(final Address address) {
    final Hash addressHash = Hash.hash(address);
    return accountStateTrie
        .get(Hash.hash(address))
        .map(bytes -> deserializeAccount(address, addressHash, bytes))
        .orElse(null);
  }

  private AccountState deserializeAccount(
      final Address address, final Hash addressHash, final BytesValue encoded) throws RLPException {
    final RLPInput in = RLP.input(encoded);
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(in);
    return new AccountState(address, addressHash, accountValue);
  }

  private static BytesValue serializeAccount(
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final int version) {
    final StateTrieAccountValue accountValue =
        new StateTrieAccountValue(nonce, balance, storageRoot, codeHash, version);
    return RLP.encode(accountValue::writeTo);
  }

  @Override
  public WorldUpdater updater() {
    return new Updater(this);
  }

  @Override
  public Stream<Account> streamAccounts() {
    // TODO: the current trie implementation doesn't have walking capability yet (pending NC-746)
    // so this can't be implemented.
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rootHash());
  }

  @Override
  public final boolean equals(final Object other) {
    if (!(other instanceof DefaultMutableWorldState)) {
      return false;
    }

    final DefaultMutableWorldState that = (DefaultMutableWorldState) other;
    return this.rootHash().equals(that.rootHash());
  }

  @Override
  public void persist() {
    final WorldStateStorage.Updater stateUpdater = worldStateStorage.updater();
    // Store updated code
    for (final BytesValue code : updatedAccountCode.values()) {
      stateUpdater.putCode(code);
    }
    // Commit account storage tries
    for (final MerklePatriciaTrie<Bytes32, BytesValue> updatedStorage :
        updatedStorageTries.values()) {
      updatedStorage.commit(stateUpdater::putAccountStorageTrieNode);
    }
    // Commit account updates
    accountStateTrie.commit(stateUpdater::putAccountStateTrieNode);

    // Persist preimages
    final WorldStatePreimageStorage.Updater preimageUpdater = preimageStorage.updater();
    newStorageKeyPreimages.forEach(preimageUpdater::putStorageTrieKeyPreimage);

    // Clear pending changes that we just flushed
    updatedStorageTries.clear();
    updatedAccountCode.clear();
    newStorageKeyPreimages.clear();

    // Push changes to underlying storage
    preimageUpdater.commit();
    stateUpdater.commit();
  }

  private Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
    return Optional.ofNullable(newStorageKeyPreimages.get(trieKey))
        .or(() -> preimageStorage.getStorageTrieKeyPreimage(trieKey));
  }

  // An immutable class that represents an individual account as stored in
  // in the world state's underlying merkle patricia trie.
  protected class AccountState implements Account {

    private final Address address;
    private final Hash addressHash;

    final StateTrieAccountValue accountValue;

    // Lazily initialized since we don't always access storage.
    private volatile MerklePatriciaTrie<Bytes32, BytesValue> storageTrie;

    private AccountState(
        final Address address, final Hash addressHash, final StateTrieAccountValue accountValue) {

      this.address = address;
      this.addressHash = addressHash;
      this.accountValue = accountValue;
    }

    private MerklePatriciaTrie<Bytes32, BytesValue> storageTrie() {
      final MerklePatriciaTrie<Bytes32, BytesValue> updatedTrie = updatedStorageTries.get(address);
      if (updatedTrie != null) {
        storageTrie = updatedTrie;
      }
      if (storageTrie == null) {
        storageTrie = newAccountStorageTrie(getStorageRoot());
      }
      return storageTrie;
    }

    @Override
    public Address getAddress() {
      return address;
    }

    @Override
    public Hash getAddressHash() {
      return addressHash;
    }

    @Override
    public long getNonce() {
      return accountValue.getNonce();
    }

    @Override
    public Wei getBalance() {
      return accountValue.getBalance();
    }

    Hash getStorageRoot() {
      return accountValue.getStorageRoot();
    }

    @Override
    public BytesValue getCode() {
      final BytesValue updatedCode = updatedAccountCode.get(address);
      if (updatedCode != null) {
        return updatedCode;
      }
      // No code is common, save the KV-store lookup.
      final Hash codeHash = getCodeHash();
      if (codeHash.equals(Hash.EMPTY)) {
        return BytesValue.EMPTY;
      }
      return worldStateStorage.getCode(codeHash).orElse(BytesValue.EMPTY);
    }

    @Override
    public boolean hasCode() {
      return !getCode().isEmpty();
    }

    @Override
    public Hash getCodeHash() {
      return accountValue.getCodeHash();
    }

    @Override
    public int getVersion() {
      return accountValue.getVersion();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
      final Optional<BytesValue> val = storageTrie().get(Hash.hash(key.getBytes()));
      if (!val.isPresent()) {
        return UInt256.ZERO;
      }
      return convertToUInt256(val.get());
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
      return getStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
        final Bytes32 startKeyHash, final int limit) {
      final NavigableMap<Bytes32, AccountStorageEntry> storageEntries = new TreeMap<>();
      storageTrie()
          .entriesFrom(startKeyHash, limit)
          .forEach(
              (key, value) -> {
                final AccountStorageEntry entry =
                    AccountStorageEntry.create(
                        convertToUInt256(value), key, getStorageTrieKeyPreimage(key));
                storageEntries.put(key, entry);
              });
      return storageEntries;
    }

    private UInt256 convertToUInt256(final BytesValue value) {
      // TODO: we could probably have an optimized method to decode a single scalar since it's used
      // pretty often.
      final RLPInput in = RLP.input(value);
      return in.readUInt256Scalar();
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("AccountState").append("{");
      builder.append("address=").append(getAddress()).append(", ");
      builder.append("nonce=").append(getNonce()).append(", ");
      builder.append("balance=").append(getBalance()).append(", ");
      builder.append("storageRoot=").append(getStorageRoot()).append(", ");
      builder.append("codeHash=").append(getCodeHash());
      builder.append("version=").append(getVersion());
      return builder.append("}").toString();
    }
  }

  protected static class Updater
      extends AbstractWorldUpdater<DefaultMutableWorldState, AccountState> {

    protected Updater(final DefaultMutableWorldState world) {
      super(world);
    }

    @Override
    protected AccountState getForMutation(final Address address) {
      final DefaultMutableWorldState wrapped = wrappedWorldView();
      final Hash addressHash = Hash.hash(address);
      return wrapped
          .accountStateTrie
          .get(addressHash)
          .map(bytes -> wrapped.deserializeAccount(address, addressHash, bytes))
          .orElse(null);
    }

    @Override
    public Collection<Account> getTouchedAccounts() {
      return new ArrayList<>(updatedAccounts());
    }

    @Override
    public void revert() {
      deletedAccounts().clear();
      updatedAccounts().clear();
    }

    @Override
    public void commit() {
      final DefaultMutableWorldState wrapped = wrappedWorldView();

      for (final Address address : deletedAccounts()) {
        final Hash addressHash = Hash.hash(address);
        wrapped.accountStateTrie.remove(addressHash);
        wrapped.updatedStorageTries.remove(address);
        wrapped.updatedAccountCode.remove(address);
      }

      for (final UpdateTrackingAccount<AccountState> updated : updatedAccounts()) {
        final AccountState origin = updated.getWrappedAccount();

        // Save the code in key-value storage ...
        Hash codeHash = origin == null ? Hash.EMPTY : origin.getCodeHash();
        if (updated.codeWasUpdated()) {
          codeHash = Hash.hash(updated.getCode());
          wrapped.updatedAccountCode.put(updated.getAddress(), updated.getCode());
        }
        // ...and storage in the account trie first.
        final boolean freshState = origin == null || updated.getStorageWasCleared();
        Hash storageRoot = freshState ? Hash.EMPTY_TRIE_HASH : origin.getStorageRoot();
        if (freshState) {
          wrapped.updatedStorageTries.remove(updated.getAddress());
        }
        final SortedMap<UInt256, UInt256> updatedStorage = updated.getUpdatedStorage();
        if (!updatedStorage.isEmpty()) {
          // Apply any storage updates
          final MerklePatriciaTrie<Bytes32, BytesValue> storageTrie =
              freshState
                  ? wrapped.newAccountStorageTrie(Hash.EMPTY_TRIE_HASH)
                  : origin.storageTrie();
          wrapped.updatedStorageTries.put(updated.getAddress(), storageTrie);
          for (final Map.Entry<UInt256, UInt256> entry : updatedStorage.entrySet()) {
            final UInt256 value = entry.getValue();
            final Hash keyHash = Hash.hash(entry.getKey().getBytes());
            if (value.isZero()) {
              storageTrie.remove(keyHash);
            } else {
              wrapped.newStorageKeyPreimages.put(keyHash, entry.getKey());
              storageTrie.put(keyHash, RLP.encode(out -> out.writeUInt256Scalar(entry.getValue())));
            }
          }
          storageRoot = Hash.wrap(storageTrie.getRootHash());
        }

        // Lastly, save the new account.
        final BytesValue account =
            serializeAccount(
                updated.getNonce(),
                updated.getBalance(),
                storageRoot,
                codeHash,
                updated.getVersion());

        wrapped.accountStateTrie.put(updated.getAddressHash(), account);
      }
    }
  }
}
