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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
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
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader.StemPreloader;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader.TrieNodePreLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader.VerklePreloader;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat.VerkleFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.verkletrie.VerkleEntryFactory;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.HashSet;
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

  private final VerklePreloader verklePreloader;

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
    this.verklePreloader =
        new VerklePreloader(new StemPreloader(), new TrieNodePreLoader(worldStateKeyValueStorage));
    this.setAccumulator(
        new VerkleWorldStateUpdateAccumulator(
            this,
            (addr, value) -> verklePreloader.preLoadAccount(addr),
            verklePreloader::preLoadStorageSlot,
            verklePreloader::preLoadCode,
            evmConfiguration));
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
            (location, hash) -> verklePreloader.getTrieNodePreLoader().getStateTrieNode(location));

    final Map<Address, AccountStateUpdateContext> preloadedHashers = new ConcurrentHashMap<>();

    worldStateUpdater.getAccountsToUpdate().entrySet().parallelStream()
        .forEach(
            accountUpdate -> {
              final Address accountKey = accountUpdate.getKey();
              final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>
                  storageAccountUpdate = worldStateUpdater.getStorageToUpdate().get(accountKey);
              final DiffBasedValue<Bytes> codeUpdate =
                  worldStateUpdater.getCodeToUpdate().get(accountKey);
              // We load all stems by address to use them later.
              // We will generate by batch what is not already preloaded (in the cache).

              Set<StorageSlotKey> storageSlotKeys = new HashSet<>();
              if (storageAccountUpdate != null) {
                storageSlotKeys = storageAccountUpdate.keySet();
              }

              Optional<Bytes> maybeCode = Optional.empty();
              if (codeUpdate != null) {
                final Bytes previousCode = codeUpdate.getPrior();
                final Bytes updatedCode = codeUpdate.getUpdated();
                if (!codeUpdate.isUnchanged()
                    && !(codeIsEmpty(previousCode) && codeIsEmpty(updatedCode))) {
                  maybeCode = Optional.of(updatedCode == null ? previousCode : updatedCode);
                }
              }

              final StemPreloader stemPreloader = verklePreloader.getStemPreloader();

              stemPreloader.preloadStemIds(accountKey, storageSlotKeys, maybeCode);

              preloadedHashers.put(
                  accountKey,
                  new AccountStateUpdateContext(
                      stemPreloader.getHasherByAddress(accountKey),
                      !storageSlotKeys.isEmpty(),
                      maybeCode.isPresent()));
            });

    for (final Map.Entry<Address, DiffBasedValue<VerkleAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Address accountKey = accountUpdate.getKey();
      final AccountStateUpdateContext hasherContext = preloadedHashers.get(accountKey);
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

    // LOG.info(stateTrie.toDotTree());
    final Bytes32 rootHash = stateTrie.getRootHash();
    LOG.info("end commit " + rootHash);
    verklePreloader.reset();
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
    final Bytes preloadedStemId = verklePreloader.getStemPreloader().preloadAccountStemId(address);
    final Optional<Bytes> stem = verklePreloader.getTrieNodePreLoader().getStem(preloadedStemId);
    return stem.map(this::decodeStemNode)
        .map(
            values -> {
              return new VerkleAccount(
                  accumulator,
                  address,
                  address.addressHash(),
                  UInt256.fromBytes(values.get(2).reverse()).toLong(),
                  Wei.of(UInt256.fromBytes(values.get(1).reverse())),
                  UInt256.fromBytes(values.get(4).reverse()).toLong(),
                  Hash.wrap(values.get(3)),
                  true);
            })
        .orElse(null);
  }

  @Override
  public Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash) {
    return getWorldStateStorage().getCode(codeHash, address.addressHash());
  }

  private List<UInt256> decodeStemNode(final Bytes encodedValues) {
    RLPInput input = new BytesValueRLPInput(encodedValues, false);
    input.enterList();
    input.skipNext(); // depth
    input.skipNext(); // commitment
    input.skipNext(); // leftCommitment
    input.skipNext(); // rightCommitment
    input.skipNext(); // leftScalar
    input.skipNext(); // rightScalar
    return input.readList(
        rlpInput -> {
          return UInt256.fromBytes(Bytes32.leftPad(rlpInput.readBytes()));
        });
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
    final Bytes preloadSlotStemId =
        verklePreloader.getStemPreloader().preloadSlotStemId(address, storageSlotKey);
    final Optional<List<UInt256>> stem =
        verklePreloader.getTrieNodePreLoader().getStem(preloadSlotStemId).map(this::decodeStemNode);
    return stem.map(
        values ->
            values.get(
                verklePreloader
                    .getStemPreloader()
                    .getStorageKeySuffix(storageSlotKey.getSlotKey().orElseThrow())
                    .intValue()));
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    throw new UnsupportedOperationException("getAllAccountStorage not yet available for verkle");
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  private VerkleTrie createTrie(final NodeLoader nodeLoader) {
    return new VerkleTrie(nodeLoader);
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
                noOpSegmentedTx,
                noOpTx,
                (VerkleFlatDbStrategy) worldStateKeyValueStorage.getFlatDbStrategy())),
        accumulator.copy());
  }

  @Override
  protected Hash getEmptyTrieHash() {
    return Hash.wrap(Bytes32.ZERO);
  }

  record AccountStateUpdateContext(
      Hasher hasher, boolean hasStorageTrieKeys, boolean hasCodeTrieKeys) {}
}
