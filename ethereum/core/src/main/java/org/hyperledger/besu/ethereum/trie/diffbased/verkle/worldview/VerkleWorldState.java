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
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;
import org.hyperledger.besu.ethereum.trie.verkle.util.SuffixTreeDecoder;
import org.hyperledger.besu.ethereum.verkletrie.VerkleEntryFactory;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.ArrayList;
import java.util.HashSet;
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
                        (location, hash) -> worldStateKeyValueStorage.getStateTrieNode(location),
                        worldStateRootHash);

        final Map<Address, Hasher> preloadedHashers = new ConcurrentHashMap<>();

        final Set<Address> addressesToPersist = getAddressesToPersist(worldStateUpdater);
        addressesToPersist.parallelStream()
                .forEach(
                        accountKey -> {

                            final StemPreloader stemPreloader = verklePreloader.getStemPreloader();

                            final DiffBasedValue<VerkleAccount> accountUpdate =
                                    worldStateUpdater.getAccountsToUpdate().get(accountKey);
                            // generate account triekeys
                            final Set<Bytes32> keys = new HashSet<>();
                            if (accountUpdate != null && !accountUpdate.isUnchanged()) {
                                keys.add(stemPreloader.generateAccountKeyId());
                                if (accountUpdate.getPrior() == null) {
                                    keys.add(Parameters.CODE_HASH_LEAF_KEY);
                                }
                            }

                            // generate storage triekeys
                            final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>
                                    storageAccountUpdate = worldStateUpdater.getStorageToUpdate().get(accountKey);
                            boolean isStorageUpdateNeeded;
                            if (storageAccountUpdate != null) {
                                final Set<StorageSlotKey> storageSlotKeys = storageAccountUpdate.keySet();
                                isStorageUpdateNeeded = !storageSlotKeys.isEmpty();
                                if (isStorageUpdateNeeded) {
                                    keys.addAll(stemPreloader.generateStorageKeyIds(storageSlotKeys));
                                }
                            }

                            // generate code triekeys
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
                                    keys.add(Parameters.CODE_HASH_LEAF_KEY);
                                    keys.addAll(
                                            stemPreloader.generateCodeChunkKeyIds(
                                                    updatedCode == null ? previousCode : updatedCode));
                                }
                            }
                            stemPreloader.preloadStemIds(accountKey, keys);

                            preloadedHashers.put(
                                    accountKey,
                                    stemPreloader.getHasherByAddress(accountKey));
                        });

        for (final Address accountKey : addressesToPersist) {
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
            final VerkleWorldStateUpdateAccumulator worldStateUpdater) {
        var accountUpdate = worldStateUpdater.getAccountsToUpdate().get(accountKey);
        if (accountUpdate == null || accountUpdate.isUnchanged()) {
            return;
        }
        if (accountUpdate.getUpdated() == null) {
            verkleEntryFactory.generateAccountKeyForRemoval(accountKey);
            return;
        }

        handleCoupledCodeAccountUpdates(
                accountKey, verkleEntryFactory, accountUpdate, worldStateUpdater);

        final VerkleAccount updatedAcount = accountUpdate.getUpdated();
        verkleEntryFactory.generateAccountKeyValueForUpdate(
                accountKey, updatedAcount.getNonce(), updatedAcount.getBalance());
    }

    private void handleCoupledCodeAccountUpdates(
            final Address accountKey,
            final VerkleEntryFactory verkleEntryFactory,
            final DiffBasedValue<VerkleAccount> accountUpdate,
            final VerkleWorldStateUpdateAccumulator worldStateUpdater) {
        final VerkleAccount priorAccount = accountUpdate.getPrior();
        final VerkleAccount updatedAccount = accountUpdate.getUpdated();
        if (priorAccount == null) {
            verkleEntryFactory.generateCodeHashKeyValueForUpdate(
                    accountKey, updatedAccount.getCodeHash());
            return;
        }
        Optional<Bytes> currentCode =
                worldStateUpdater.getCode(accountKey, updatedAccount.getCodeHash());
        currentCode.ifPresent(
                code -> verkleEntryFactory.generateCodeSizeKeyValueForUpdate(accountKey, code.size()));
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
                    verkleEntryFactory.generateStorageKeyForRemoval(accountKey, storageUpdate.getKey());
                } else {
                    verkleEntryFactory.generateStorageKeyValueForUpdate(
                            accountKey, storageUpdate.getKey(), updatedStorage);
                }
            }
        }
    }

    private void updateState(
            final Address accountKey,
            final VerkleTrie stateTrie,
            final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
            final Hasher hasher,
            final VerkleWorldStateUpdateAccumulator worldStateUpdater) {

        final VerkleEntryFactory verkleEntryFactory = new VerkleEntryFactory(hasher);

        generateAccountValues(accountKey, verkleEntryFactory, maybeStateUpdater, worldStateUpdater);

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
                            System.out.println("add key " + key + " leaf value " + value);
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

    public Set<Address> getAddressesToPersist(
            final DiffBasedWorldStateUpdateAccumulator<?> accumulator) {
        Set<Address> mergedAddresses =
                new HashSet<>(accumulator.getAccountsToUpdate().keySet()); // accountsToUpdate
        mergedAddresses.addAll(accumulator.getCodeToUpdate().keySet()); // codeToUpdate
        mergedAddresses.addAll(accumulator.getStorageToClear()); // storageToClear
        mergedAddresses.addAll(accumulator.getStorageToUpdate().keySet()); // storageToUpdate
        return mergedAddresses;
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
        final Optional<List<Bytes32>> stem = verklePreloader.getTrieNodePreLoader().getDecodedStem(preloadedStemId);
        return stem.map(
                        values -> {
                            return new VerkleAccount(
                                    accumulator,
                                    address,
                                    address.addressHash(),
                                    SuffixTreeDecoder.decodeNonce(values.get(0)),
                                    Wei.of(SuffixTreeDecoder.decodeBalance(values.get(0))),
                                            SuffixTreeDecoder.decodeCodeSize(values.get(0)),
                                    Hash.wrap(values.get(1)),
                                    true);
                        })
                .orElse(null);
    }

    @Override
    public Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash) {
        return getWorldStateStorage().getCode(codeHash, address.addressHash());
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
        final Optional<List<Bytes32>> stem =
                verklePreloader.getTrieNodePreLoader().getDecodedStem(preloadSlotStemId);
        return stem.map(
                values ->
                        values.get(
                                verklePreloader
                                        .getStemPreloader()
                                        .getStorageKeySuffix(storageSlotKey.getSlotKey().orElseThrow())
                                        .intValue())).map(UInt256::fromBytes);
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
        return new VerkleTrie(nodeLoader);
    }

    protected void writeTrieNode(
            final SegmentIdentifier segmentId,
            final SegmentedKeyValueStorageTransaction tx,
            final Bytes location,
            final Bytes value) {
        tx.put(segmentId, location.toArrayUnsafe(), value.toArrayUnsafe());
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