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
 */
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView.encodeTrieValue;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.NoOpMerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.cache.DiffBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldStateConfig;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiWorldState extends DiffBasedWorldState {

  protected final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader;

  public BonsaiWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration,
      final DiffBasedWorldStateConfig diffBasedWorldStateConfig) {
    this(
        worldStateKeyValueStorage,
        archive.getCachedMerkleTrieLoader(),
        archive.getCachedWorldStorageManager(),
        archive.getTrieLogManager(),
        evmConfiguration,
        diffBasedWorldStateConfig);
  }

  public BonsaiWorldState(
      final BonsaiWorldState worldState,
      final BonsaiCachedMerkleTrieLoader cachedMerkleTrieLoader) {
    this(
        new BonsaiWorldStateLayerStorage(worldState.getWorldStateStorage()),
        cachedMerkleTrieLoader,
        worldState.cachedWorldStorageManager,
        worldState.trieLogManager,
        worldState.accumulator.getEvmConfiguration(),
        new DiffBasedWorldStateConfig(worldState.worldStateConfig));
  }

  public BonsaiWorldState(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final DiffBasedCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final EvmConfiguration evmConfiguration,
      final DiffBasedWorldStateConfig diffBasedWorldStateConfig) {
    super(
        worldStateKeyValueStorage,
        cachedWorldStorageManager,
        trieLogManager,
        diffBasedWorldStateConfig);
    this.bonsaiCachedMerkleTrieLoader = bonsaiCachedMerkleTrieLoader;
    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    this.setAccumulator(
        new BonsaiWorldStateUpdateAccumulator(
            this,
            (addr, value) ->
                bonsaiCachedMerkleTrieLoader.preLoadAccount(
                    getWorldStateStorage(), worldStateRootHash, addr),
            (addr, value) ->
                this.bonsaiCachedMerkleTrieLoader.preLoadStorageSlot(
                    getWorldStateStorage(), addr, value),
            evmConfiguration));
  }

  @Override
  public Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash) {
    return getWorldStateStorage().getCode(codeHash, address.addressHash());
  }

  @Override
  public BonsaiWorldStateKeyValueStorage getWorldStateStorage() {
    return (BonsaiWorldStateKeyValueStorage) worldStateKeyValueStorage;
  }

  @Override
  protected Hash calculateRootHash(
      final Optional<DiffBasedWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final DiffBasedWorldStateUpdateAccumulator<?> worldStateUpdater) {
    return internalCalculateRootHash(
        maybeStateUpdater.map(BonsaiWorldStateKeyValueStorage.Updater.class::cast),
        (BonsaiWorldStateUpdateAccumulator) worldStateUpdater);
  }

  private Hash internalCalculateRootHash(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {

    clearStorage(maybeStateUpdater, worldStateUpdater);

    // This must be done before updating the accounts so
    // that we can get the storage state hash
    Stream<Map.Entry<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>>
        storageStream = worldStateUpdater.getStorageToUpdate().entrySet().stream();
    if (maybeStateUpdater.isEmpty()) {
      storageStream =
          storageStream
              .parallel(); // if we are not updating the state updater we can use parallel stream
    }
    storageStream.forEach(
        addressMapEntry ->
            updateAccountStorageState(maybeStateUpdater, worldStateUpdater, addressMapEntry));

    // Third update the code.  This has the side effect of ensuring a code hash is calculated.
    updateCode(maybeStateUpdater, worldStateUpdater);

    // next walk the account trie
    final MerkleTrie<Bytes, Bytes> accountTrie =
        createTrie(
            (location, hash) ->
                bonsaiCachedMerkleTrieLoader.getAccountStateTrieNode(
                    getWorldStateStorage(), location, hash),
            worldStateRootHash);

    // for manicured tries and composting, collect branches here (not implemented)
    updateTheAccounts(maybeStateUpdater, worldStateUpdater, accountTrie);

    // TODO write to a cache and then generate a layer update from that and the
    // DB tx updates.  Right now it is just DB updates.
    maybeStateUpdater.ifPresent(
        bonsaiUpdater ->
            accountTrie.commit(
                (location, hash, value) ->
                    writeTrieNode(
                        TRIE_BRANCH_STORAGE,
                        bonsaiUpdater.getWorldStateTransaction(),
                        location,
                        value)));
    final Bytes32 rootHash = accountTrie.getRootHash();
    return Hash.wrap(rootHash);
  }

  private void updateTheAccounts(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final MerkleTrie<Bytes, Bytes> accountTrie) {
    for (final Map.Entry<Address, DiffBasedValue<BonsaiAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Bytes accountKey = accountUpdate.getKey();
      final DiffBasedValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
      final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
      try {
        if (updatedAccount == null) {
          final Hash addressHash = hashAndSavePreImage(accountKey);
          accountTrie.remove(addressHash);
          maybeStateUpdater.ifPresent(
              bonsaiUpdater -> bonsaiUpdater.removeAccountInfoState(addressHash));
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
          final Bytes accountValue = updatedAccount.serializeAccount();
          maybeStateUpdater.ifPresent(
              bonsaiUpdater ->
                  bonsaiUpdater.putAccountInfoState(hashAndSavePreImage(accountKey), accountValue));
          accountTrie.put(addressHash, accountValue);
        }
      } catch (MerkleTrieException e) {
        // need to throw to trigger the heal
        throw new MerkleTrieException(
            e.getMessage(), Optional.of(Address.wrap(accountKey)), e.getHash(), e.getLocation());
      }
    }
  }

  @VisibleForTesting
  protected void updateCode(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {
    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          for (final Map.Entry<Address, DiffBasedValue<Bytes>> codeUpdate :
              worldStateUpdater.getCodeToUpdate().entrySet()) {
            final Bytes updatedCode = codeUpdate.getValue().getUpdated();
            final Hash accountHash = codeUpdate.getKey().addressHash();
            final Bytes priorCode = codeUpdate.getValue().getPrior();

            // code hasn't changed then do nothing
            if (Objects.equals(priorCode, updatedCode)
                || (codeIsEmpty(priorCode) && codeIsEmpty(updatedCode))) {
              continue;
            }

            if (codeIsEmpty(updatedCode)) {
              final Hash priorCodeHash = Hash.hash(priorCode);
              bonsaiUpdater.removeCode(accountHash, priorCodeHash);
            } else {
              final Hash codeHash = Hash.hash(codeUpdate.getValue().getUpdated());
              bonsaiUpdater.putCode(accountHash, codeHash, updatedCode);
            }
          }
        });
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  private void updateAccountStorageState(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final Map.Entry<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
          storageAccountUpdate) {
    final Address updatedAddress = storageAccountUpdate.getKey();
    final Hash updatedAddressHash = updatedAddress.addressHash();
    if (worldStateUpdater.getAccountsToUpdate().containsKey(updatedAddress)) {
      final DiffBasedValue<BonsaiAccount> accountValue =
          worldStateUpdater.getAccountsToUpdate().get(updatedAddress);
      final BonsaiAccount accountOriginal = accountValue.getPrior();
      final Hash storageRoot =
          (accountOriginal == null
                  || worldStateUpdater.getStorageToClear().contains(updatedAddress))
              ? Hash.EMPTY_TRIE_HASH
              : accountOriginal.getStorageRoot();
      final MerkleTrie<Bytes, Bytes> storageTrie =
          createTrie(
              (location, key) ->
                  bonsaiCachedMerkleTrieLoader.getAccountStorageTrieNode(
                      getWorldStateStorage(), updatedAddressHash, location, key),
              storageRoot);

      // for manicured tries and composting, collect branches here (not implemented)
      for (final Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>> storageUpdate :
          storageAccountUpdate.getValue().entrySet()) {
        final Hash slotHash = storageUpdate.getKey().getSlotHash();
        final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
        try {
          if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.removeStorageValueBySlotHash(updatedAddressHash, slotHash));
            storageTrie.remove(slotHash);
          } else {
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.putStorageValueBySlotHash(
                        updatedAddressHash, slotHash, updatedStorage));
            storageTrie.put(slotHash, encodeTrieValue(updatedStorage));
          }
        } catch (MerkleTrieException e) {
          // need to throw to trigger the heal
          throw new MerkleTrieException(
              e.getMessage(),
              Optional.of(Address.wrap(updatedAddress)),
              e.getHash(),
              e.getLocation());
        }
      }

      final BonsaiAccount accountUpdated = accountValue.getUpdated();
      if (accountUpdated != null) {
        maybeStateUpdater.ifPresent(
            bonsaiUpdater ->
                storageTrie.commit(
                    (location, key, value) ->
                        writeStorageTrieNode(
                            bonsaiUpdater, updatedAddressHash, location, key, value)));
        // only use storage root of the trie when trie is enabled
        if (!worldStateConfig.isTrieDisabled()) {
          final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
          accountUpdated.setStorageRoot(newStorageRoot);
        }
      }
    }
    // for manicured tries and composting, trim and compost here
  }

  private void clearStorage(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {
    for (final Address address : worldStateUpdater.getStorageToClear()) {
      // because we are clearing persisted values we need the account root as persisted
      final BonsaiAccount oldAccount =
          getWorldStateStorage()
              .getAccount(address.addressHash())
              .map(bytes -> BonsaiAccount.fromRLP(BonsaiWorldState.this, address, bytes, true))
              .orElse(null);
      if (oldAccount == null) {
        // This is when an account is both created and deleted within the scope of the same
        // block.  A not-uncommon DeFi bot pattern.
        continue;
      }
      final Hash addressHash = address.addressHash();
      final MerkleTrie<Bytes, Bytes> storageTrie =
          createTrie(
              (location, key) -> getStorageTrieNode(addressHash, location, key),
              oldAccount.getStorageRoot());
      try {
        final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageToDelete =
            worldStateUpdater.getStorageToUpdate().get(address);
        Map<Bytes32, Bytes> entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
        while (!entriesToDelete.isEmpty()) {
          entriesToDelete.forEach(
              (k, v) -> {
                final StorageSlotKey storageSlotKey =
                    new StorageSlotKey(Hash.wrap(k), Optional.empty());
                final UInt256 slotValue = UInt256.fromBytes(Bytes32.leftPad(RLP.decodeValue(v)));
                maybeStateUpdater.ifPresent(
                    bonsaiUpdater ->
                        bonsaiUpdater.removeStorageValueBySlotHash(
                            address.addressHash(), storageSlotKey.getSlotHash()));
                storageToDelete
                    .computeIfAbsent(
                        storageSlotKey, key -> new DiffBasedValue<>(slotValue, null, true))
                    .setPrior(slotValue);
              });
          entriesToDelete.keySet().forEach(storageTrie::remove);
          if (entriesToDelete.size() == 256) {
            entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
          } else {
            break;
          }
        }
      } catch (MerkleTrieException e) {
        // need to throw to trigger the heal
        throw new MerkleTrieException(
            e.getMessage(), Optional.of(Address.wrap(address)), e.getHash(), e.getLocation());
      }
    }
  }

  @Override
  public Hash frontierRootHash() {
    return calculateRootHash(
        Optional.of(
            new BonsaiWorldStateKeyValueStorage.Updater(
                noOpSegmentedTx, noOpTx, worldStateKeyValueStorage.getFlatDbStrategy())),
        accumulator.copy());
  }

  @Override
  public Account get(final Address address) {
    return getWorldStateStorage()
        .getAccount(address.addressHash())
        .map(bytes -> BonsaiAccount.fromRLP(accumulator, address, bytes, true))
        .orElse(null);
  }

  protected Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return getWorldStateStorage().getAccountStateTrieNode(location, nodeHash);
  }

  private void writeTrieNode(
      final SegmentIdentifier segmentId,
      final SegmentedKeyValueStorageTransaction tx,
      final Bytes location,
      final Bytes value) {
    tx.put(segmentId, location.toArrayUnsafe(), value.toArrayUnsafe());
  }

  protected Optional<Bytes> getStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return getWorldStateStorage().getAccountStorageTrieNode(accountHash, location, nodeHash);
  }

  private void writeStorageTrieNode(
      final BonsaiWorldStateKeyValueStorage.Updater stateUpdater,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash,
      final Bytes value) {
    stateUpdater.putAccountStorageTrieNode(accountHash, location, nodeHash, value);
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValueByStorageSlotKey(address, new StorageSlotKey(storageKey))
        .orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    return getWorldStateStorage()
        .getStorageValueByStorageSlotKey(address.addressHash(), storageSlotKey)
        .map(UInt256::fromBytes);
  }

  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Address address,
      final StorageSlotKey storageSlotKey) {
    return getWorldStateStorage()
        .getStorageValueByStorageSlotKey(storageRootSupplier, address.addressHash(), storageSlotKey)
        .map(UInt256::fromBytes);
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    final MerkleTrie<Bytes, Bytes> storageTrie =
        createTrie(
            (location, key) -> getStorageTrieNode(address.addressHash(), location, key), rootHash);
    return storageTrie.entriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
  }

  @Override
  public MutableWorldState freeze() {
    this.worldStateConfig.setFrozen(true);
    this.worldStateKeyValueStorage = new BonsaiWorldStateLayerStorage(getWorldStateStorage());
    return this;
  }

  private MerkleTrie<Bytes, Bytes> createTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    if (worldStateConfig.isTrieDisabled()) {
      return new NoOpMerkleTrie<>();
    } else {
      return new StoredMerklePatriciaTrie<>(
          nodeLoader, rootHash, Function.identity(), Function.identity());
    }
  }

  protected Hash hashAndSavePreImage(final Bytes value) {
    // by default do not save has preImages
    return Hash.hash(value);
  }

  @Override
  protected Hash getEmptyTrieHash() {
    return Hash.EMPTY_TRIE_HASH;
  }
}
