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
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat.FlatBasicData;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat.VerkleFlatDbStrategy;
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

import kotlin.Pair;
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
              final List<Bytes32> accountKeyIds =
                  new ArrayList<>(trieKeyPreloader.generateAccountKeyIds());

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
                  accountKeyIds.add(Parameters.CODE_SIZE_LEAF_KEY);
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

    for (final Map.Entry<Address, DiffBasedValue<VerkleAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Address accountKey = accountUpdate.getKey();
      final HasherContext hasherContext = preloadedHashers.get(accountKey);
      final VerkleEntryFactory verkleEntryFactory = new VerkleEntryFactory(hasherContext.hasher());
      if (hasherContext.hasStorageTrieKeys()) {
        final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageAccountUpdate =
            worldStateUpdater.getStorageToUpdate().get(accountKey);
        updateAccountStorageState(
            accountKey, stateTrie, maybeStateUpdater, verkleEntryFactory, storageAccountUpdate);
      }
      if (hasherContext.hasCodeTrieKeys()) {
        final DiffBasedValue<Bytes> codeUpdate =
            worldStateUpdater.getCodeToUpdate().get(accountKey);
        updateCode(accountKey, stateTrie, maybeStateUpdater, verkleEntryFactory, codeUpdate);
      }
      updateTheAccount(
          accountKey, stateTrie, maybeStateUpdater, verkleEntryFactory, accountUpdate.getValue());
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

  private void updateTheAccount(
      final Address accountKey,
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleEntryFactory verkleEntryFactory,
      final DiffBasedValue<VerkleAccount> accountUpdate) {

    if (!accountUpdate.isUnchanged()) {
      final VerkleAccount priorAccount = accountUpdate.getPrior();
      final VerkleAccount updatedAccount = accountUpdate.getUpdated();
      if (updatedAccount == null) {
        final Hash addressHash = hashAndSavePreImage(accountKey);
        verkleEntryFactory
            .generateKeysForAccount(accountKey)
            .forEach(
                bytes -> {
                  System.out.println(
                      "remove "
                          + accountKey
                          + " "
                          + bytes
                          + " "
                          + accountUpdate.getPrior()
                          + " "
                          + accountUpdate.getUpdated());
                  stateTrie.remove(bytes);
                });
      } else {
        final Bytes priorValue = priorAccount == null ? null : priorAccount.serializeAccount();
        final Bytes accountValue = updatedAccount.serializeAccount();
        if (!accountValue.equals(priorValue)) {
          verkleEntryFactory
              .generateKeyValuesForAccount(
                  accountKey,
                  updatedAccount.getNonce(),
                  updatedAccount.getBalance(),
                  updatedAccount.getCodeHash())
              .forEach(
                  (bytes, bytes2) -> {
                    System.out.println(
                        "add "
                            + accountKey
                            + " "
                            + bytes
                            + " "
                            + bytes2
                            + " "
                            + updatedAccount.getBalance());
                    stateTrie.put(bytes, bytes2);
                  });
        }
      }
    }
  }

  private void updateCode(
      final Address accountKey,
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleEntryFactory verkleEntryFactory,
      final DiffBasedValue<Bytes> codeUpdate) {
    final Bytes priorCode = codeUpdate.getPrior();
    final Bytes updatedCode = codeUpdate.getUpdated();
    final Hash accountHash = accountKey.addressHash();
    if (updatedCode == null) {
      final Hash priorCodeHash = Hash.hash(priorCode);
      verkleEntryFactory
          .generateKeysForCode(accountKey, priorCode)
          .forEach(
              bytes -> {
                System.out.println("remove code " + bytes);
                stateTrie.remove(bytes);
              });
      maybeStateUpdater.ifPresent(
          bonsaiUpdater -> bonsaiUpdater.removeCode(accountHash, priorCodeHash));
    } else {
      if (updatedCode.isEmpty()) {
        final Hash codeHash = Hash.hash(updatedCode);
        verkleEntryFactory
            .generateKeyValuesForCode(accountKey, updatedCode)
            .forEach(
                (bytes, bytes2) -> {
                  // System.out.println("add code " + bytes + " " + bytes2);
                  stateTrie.put(bytes, bytes2);
                });
        maybeStateUpdater.ifPresent(
            bonsaiUpdater -> bonsaiUpdater.removeCode(accountHash, codeHash));
      } else {
        final Hash codeHash = Hash.hash(updatedCode);
        verkleEntryFactory
            .generateKeyValuesForCode(accountKey, updatedCode)
            .forEach(
                (bytes, bytes2) -> {
                  System.out.println("add code " + bytes + " " + bytes2);
                  stateTrie.put(bytes, bytes2);
                });
        maybeStateUpdater.ifPresent(
            bonsaiUpdater -> bonsaiUpdater.putCode(accountHash, codeHash, updatedCode));
      }
    }
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  private void updateAccountStorageState(
      final Address accountKey,
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleEntryFactory verkleEntryFactory,
      final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageAccountUpdate) {

    final Hash updatedAddressHash = accountKey.addressHash();
    // for manicured tries and composting, collect branches here (not implemented)
    for (final Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>> storageUpdate :
        storageAccountUpdate.entrySet()) {
      final Hash slotHash = storageUpdate.getKey().getSlotHash();

      if (!storageUpdate.getValue().isUnchanged()) {
        final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
        if (updatedStorage == null) {
          verkleEntryFactory
              .generateKeysForStorage(accountKey, storageUpdate.getKey())
              .forEach(
                  bytes -> {
                    System.out.println("remove storage" + bytes);
                    stateTrie.remove(bytes);
                  });
        } else {
          final Pair<Bytes, Bytes> storage =
              verkleEntryFactory.generateKeyValuesForStorage(
                  accountKey, storageUpdate.getKey(), updatedStorage);
          System.out.println("add storage " + storage.getFirst() + " " + storage.getSecond());
          stateTrie
              .put(storage.getFirst(), storage.getSecond())
              .ifPresentOrElse(
                  bytes -> {
                    storageUpdate.getValue().setPrior(UInt256.fromBytes(bytes));
                  },
                  () -> {
                    storageUpdate.getValue().setPrior(null);
                  });
        }
      }
    }
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
    return getBasicFlatData(address).getVerkleAccount();
  }

  public FlatBasicData getBasicFlatData(final Address address) {
    return getWorldStateStorage()
            .getFlatBasicData(address)
            .map(bytes -> FlatBasicData.fromRLP(accumulator, address, bytes, true))
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
        .getStorageValueByStorageSlotKey(address, storageSlotKey)
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
                noOpSegmentedTx, noOpTx, (VerkleFlatDbStrategy) worldStateKeyValueStorage.getFlatDbStrategy())),
        accumulator.copy());
  }

  @Override
  protected Hash getEmptyTrieHash() {
    return Hash.wrap(Bytes32.ZERO);
  }
}
