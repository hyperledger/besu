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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.cache.DiffBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldStateConfig;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;
import org.hyperledger.besu.ethereum.verkletrie.TrieKeyPreloader;
import org.hyperledger.besu.ethereum.verkletrie.TrieKeyPreloader.HasherContext;
import org.hyperledger.besu.ethereum.verkletrie.VerkleEntryFactory;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection", "ModifiedButNotUsed"})
public class VerkleWorldState extends DiffBasedWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(VerkleWorldState.class);

  private final TrieKeyPreloader trieKeyPreloader;

  public VerkleWorldState(
      final VerkleWorldStateProvider archive,
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration,
      final DiffBasedWorldStateConfig diffBasedWorldStateConfig) {
    this(
        worldStateKeyValueStorage,
        archive.getCachedWorldStorageManager(),
        archive.getTrieLogManager(),
        evmConfiguration,
        diffBasedWorldStateConfig);
  }

  public VerkleWorldState(
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final DiffBasedCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final EvmConfiguration evmConfiguration,
      final DiffBasedWorldStateConfig diffBasedWorldStateConfig) {
    super(
        worldStateKeyValueStorage,
        cachedWorldStorageManager,
        trieLogManager,
        diffBasedWorldStateConfig);
    this.trieKeyPreloader = new TrieKeyPreloader();
    this.setAccumulator(
        new VerkleWorldStateUpdateAccumulator(
            this, (addr, value) -> {}, (addr, value) -> {}, evmConfiguration));
  }

  @Override
  public VerkleWorldStateKeyValueStorage getWorldStateStorage() {
    return (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage;
  }

  @Override
  protected Hash calculateRootHash(
      final Optional<DiffBasedWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final DiffBasedWorldStateUpdateAccumulator<?> worldStateUpdater) {
    return internalCalculateRootHash(
        maybeStateUpdater.map(VerkleWorldStateKeyValueStorage.Updater.class::cast),
        (VerkleWorldStateUpdateAccumulator) worldStateUpdater);
  }

  protected Hash internalCalculateRootHash(
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleWorldStateUpdateAccumulator worldStateUpdater) {

    final VerkleTrie stateTrie =
        createTrie(
            (location, hash) -> worldStateKeyValueStorage.getStateTrieNode(location),
            worldStateRootHash);

    final Map<Address, HasherContext> preloadedHashers = new ConcurrentHashMap<>();

    worldStateUpdater.getAccountsToUpdate().entrySet().parallelStream()
        .forEach(
            accountUpdate -> {
              final Address accountKey = accountUpdate.getKey();
              // generate account triekeys
              final List<Bytes32> accountKeyIds = new ArrayList<>();
              if (!accountUpdate.getValue().isUnchanged()) {
                accountKeyIds.add(trieKeyPreloader.generateAccountKeyId());
              }

              // generate storage triekeys
              final List<Bytes32> storageKeyIds = new ArrayList<>();
              final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>
                  storageAccountUpdate = worldStateUpdater.getStorageToUpdate().get(accountKey);
              boolean isStorageUpdateNeeded;
              if (storageAccountUpdate != null) {
                final Set<StorageSlotKey> storageSlotKeys = storageAccountUpdate.keySet();
                isStorageUpdateNeeded = !storageSlotKeys.isEmpty();
                if (isStorageUpdateNeeded) {
                  storageKeyIds.addAll(trieKeyPreloader.generateStorageKeyIds(storageSlotKeys));
                }
              }

              // generate code triekeys
              final List<Bytes32> codeKeyIds = new ArrayList<>();
              final DiffBasedValue<Bytes> codeUpdate =
                  worldStateUpdater.getCodeToUpdate().get(accountKey);
              boolean isCodeUpdateNeeded;
              if (codeUpdate != null) {
                final Bytes previousCode = codeUpdate.getPrior();
                final Bytes updatedCode = codeUpdate.getUpdated();
                isCodeUpdateNeeded =
                    !codeUpdate.isUnchanged()
                        && !(codeIsEmpty(previousCode) && codeIsEmpty(updatedCode));
                if (isCodeUpdateNeeded) {
                  accountKeyIds.add(Parameters.CODE_HASH_LEAF_KEY);
                  codeKeyIds.addAll(
                      trieKeyPreloader.generateCodeChunkKeyIds(
                          updatedCode == null ? previousCode : updatedCode));
                }
              }

              preloadedHashers.put(
                  accountKey,
                  trieKeyPreloader.createPreloadedHasher(
                      accountKey, accountKeyIds, storageKeyIds, codeKeyIds));
            });

    for (final Address accountKey : worldStateUpdater.getAccountsToUpdate().keySet()) {
      updateState(
          accountKey,
          stateTrie,
          maybeStateUpdater,
          preloadedHashers.get(accountKey),
          worldStateUpdater);
    }

    LOG.info("start commit ");
    maybeStateUpdater.ifPresent(
        bonsaiUpdater ->
            stateTrie.commit(
                (location, hash, value) -> {
                  writeTrieNode(
                      TRIE_BRANCH_STORAGE,
                      bonsaiUpdater.getWorldStateTransaction(),
                      location,
                      value);
                }));

    LOG.info("end commit ");
    // LOG.info(stateTrie.toDotTree());
    final Bytes32 rootHash = stateTrie.getRootHash();
    LOG.info("end commit " + rootHash);

    return Hash.wrap(rootHash);
  }

  private static boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  private void generateAccountValues(
      final Address accountKey,
      final VerkleEntryFactory verkleEntryFactory,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final DiffBasedValue<VerkleAccount> accountUpdate) {
    if (accountUpdate.isUnchanged()) {
      return;
    }
    if (accountUpdate.getUpdated() == null) {
      verkleEntryFactory.generateAccountKeysForRemoval(accountKey);
      final Hash addressHash = hashAndSavePreImage(accountKey);
      maybeStateUpdater.ifPresent(
          bonsaiUpdater -> bonsaiUpdater.removeAccountInfoState(addressHash));
      return;
    }
    final Bytes priorValue =
        accountUpdate.getPrior() == null ? null : accountUpdate.getPrior().serializeAccount();
    final Bytes accountValue = accountUpdate.getUpdated().serializeAccount();
    if (!accountValue.equals(priorValue)) {
      verkleEntryFactory.generateAccountKeyValueForUpdate(
          accountKey,
          accountUpdate.getUpdated().getNonce(),
          accountUpdate.getUpdated().getBalance());
      maybeStateUpdater.ifPresent(
          bonsaiUpdater ->
              bonsaiUpdater.putAccountInfoState(hashAndSavePreImage(accountKey), accountValue));
    }
  }

  private void generateCodeValues(
      final Address accountKey,
      final VerkleEntryFactory verkleEntryFactory,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final DiffBasedValue<Bytes> codeUpdate) {
    if (codeUpdate == null
        || codeUpdate.isUnchanged()
        || (codeIsEmpty(codeUpdate.getPrior()) && codeIsEmpty(codeUpdate.getUpdated()))) {
      return;
    }
    if (codeUpdate.getUpdated() == null) {
      final Hash priorCodeHash = Hash.hash(codeUpdate.getPrior());
      verkleEntryFactory.generateCodeKeysForRemoval(accountKey, codeUpdate.getPrior());
      final Hash accountHash = accountKey.addressHash();
      maybeStateUpdater.ifPresent(
          bonsaiUpdater -> bonsaiUpdater.removeCode(accountHash, priorCodeHash));
      return;
    }
    final Hash accountHash = accountKey.addressHash();
    final Hash codeHash = Hash.hash(codeUpdate.getUpdated());
    verkleEntryFactory.generateCodeKeyValuesForUpdate(
        accountKey, codeUpdate.getUpdated(), codeHash);
    if (codeUpdate.getUpdated().isEmpty()) {
      maybeStateUpdater.ifPresent(bonsaiUpdater -> bonsaiUpdater.removeCode(accountHash, codeHash));
    } else {
      maybeStateUpdater.ifPresent(
          bonsaiUpdater -> bonsaiUpdater.putCode(accountHash, codeHash, codeUpdate.getUpdated()));
    }
  }

  private void generateStorageValues(
      final Address accountKey,
      final VerkleEntryFactory verkleEntryFactory,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageAccountUpdate) {
    if (storageAccountUpdate == null || storageAccountUpdate.keySet().isEmpty()) {
      return;
    }
    final Hash updatedAddressHash = accountKey.addressHash();
    // for manicured tries and composting, collect branches here (not implemented)
    for (final Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>> storageUpdate :
        storageAccountUpdate.entrySet()) {
      final Hash slotHash = storageUpdate.getKey().getSlotHash();

      if (!storageUpdate.getValue().isUnchanged()) {
        final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
        if (updatedStorage == null) {
          verkleEntryFactory.generateStorageKeysForRemoval(accountKey, storageUpdate.getKey());
          maybeStateUpdater.ifPresent(
              diffBasedUpdater ->
                  diffBasedUpdater.removeStorageValueBySlotHash(updatedAddressHash, slotHash));
        } else {
          verkleEntryFactory.generateStorageKeyValueForUpdate(
              accountKey, storageUpdate.getKey(), updatedStorage);
          maybeStateUpdater.ifPresent(
              bonsaiUpdater ->
                  bonsaiUpdater.putStorageValueBySlotHash(
                      updatedAddressHash, slotHash, updatedStorage));
        }
      }
    }
  }

  private void updateState(
      final Address accountKey,
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final HasherContext hasherContext,
      final VerkleWorldStateUpdateAccumulator worldStateUpdater) {

    final VerkleEntryFactory verkleEntryFactory = new VerkleEntryFactory(hasherContext.hasher());

    generateAccountValues(
        accountKey,
        verkleEntryFactory,
        maybeStateUpdater,
        worldStateUpdater.getAccountsToUpdate().get(accountKey));

    generateCodeValues(
        accountKey,
        verkleEntryFactory,
        maybeStateUpdater,
        worldStateUpdater.getCodeToUpdate().get(accountKey));

    generateStorageValues(
        accountKey,
        verkleEntryFactory,
        maybeStateUpdater,
        worldStateUpdater.getStorageToUpdate().get(accountKey));

    verkleEntryFactory
        .getKeysForRemoval()
        .forEach(
            key -> {
              System.out.println("remove key " + key);
              stateTrie.remove(key);
            });
    verkleEntryFactory
        .getNonStorageKeyValuesForUpdate()
        .forEach(
            (key, value) -> {
              System.out.println("add key " + key + "  leaf value " + value);
              stateTrie.put(key, value);
            });
    verkleEntryFactory
        .getStorageKeyValuesForUpdate()
        .forEach(
            (storageSlotKey, pair) -> {
              var storageAccountUpdate = worldStateUpdater.getStorageToUpdate().get(accountKey);
              if (storageAccountUpdate == null) {
                return;
              }
              System.out.println(
                  "add storage key " + pair.getFirst() + "  value " + pair.getSecond());
              Optional<DiffBasedValue<UInt256>> storageUpdate =
                  Optional.ofNullable(storageAccountUpdate.get(storageSlotKey));
              stateTrie
                  .put(pair.getFirst(), pair.getSecond())
                  .ifPresentOrElse(
                      bytes ->
                          storageUpdate.ifPresent(
                              storage -> storage.setPrior(UInt256.fromBytes(bytes))),
                      () -> storageUpdate.ifPresent(storage -> storage.setPrior(null)));
            });
  }

  @Override
  public MutableWorldState freeze() {
    this.worldStateConfig.setFrozen(true);
    this.worldStateKeyValueStorage =
        new VerkleLayeredWorldStateKeyValueStorage(getWorldStateStorage());
    return this;
  }

  @Override
  public Account get(final Address address) {
    return getWorldStateStorage()
        .getAccount(address.addressHash())
        .map(bytes -> VerkleAccount.fromRLP(accumulator, address, bytes, true))
        .orElse(null);
  }

  @Override
  public Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash) {
    return getWorldStateStorage().getCode(codeHash, address.addressHash());
  }

  protected void writeTrieNode(
      final SegmentIdentifier segmentId,
      final SegmentedKeyValueStorageTransaction tx,
      final Bytes location,
      final Bytes value) {
    tx.put(segmentId, location.toArrayUnsafe(), value.toArrayUnsafe());
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

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    throw new UnsupportedOperationException("getAllAccountStorage not yet available for verkle");
  }

  private VerkleTrie createTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    return new VerkleTrie(nodeLoader, rootHash);
  }

  protected Hash hashAndSavePreImage(final Bytes value) {
    // by default do not save has preImages
    return Hash.hash(value);
  }

  @Override
  public Hash frontierRootHash() {
    return calculateRootHash(
        Optional.of(
            new VerkleWorldStateKeyValueStorage.Updater(
                noOpSegmentedTx, noOpTx, worldStateKeyValueStorage.getFlatDbStrategy())),
        accumulator.copy());
  }

  @Override
  protected Hash getEmptyTrieHash() {
    return Hash.wrap(Bytes32.ZERO);
  }
}
