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
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrieKeyValueGenerator;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkleWorldState extends DiffBasedWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(VerkleWorldState.class);

  private final VerkleTrieKeyValueGenerator verkleTrieKeyValueGenerator =
      new VerkleTrieKeyValueGenerator();

  public VerkleWorldState(
      final VerkleWorldStateProvider archive,
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration) {
    this(
        worldStateKeyValueStorage,
        archive.getCachedWorldStorageManager(),
        archive.getTrieLogManager(),
        evmConfiguration);
  }

  public VerkleWorldState(
      final VerkleWorldStateKeyValueStorage worldStateKeyValueStorage,
      final DiffBasedCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final EvmConfiguration evmConfiguration) {
    super(worldStateKeyValueStorage, cachedWorldStorageManager, trieLogManager);
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
    // clearStorage(maybeStateUpdater, worldStateUpdater);

    Stream<Map.Entry<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>>
        storageStream = worldStateUpdater.getStorageToUpdate().entrySet().stream();
    if (maybeStateUpdater.isEmpty()) {
      storageStream =
          storageStream
              .parallel(); // if we are not updating the state updater we can use parallel stream
    }
    storageStream.forEach(
        addressMapEntry ->
            updateAccountStorageState(
                stateTrie, maybeStateUpdater, worldStateUpdater, addressMapEntry));

    // Third update the code.  This has the side effect of ensuring a code hash is calculated.
    updateCode(stateTrie, maybeStateUpdater, worldStateUpdater);

    // for manicured tries and composting, collect branches here (not implemented)
    updateTheAccounts(maybeStateUpdater, worldStateUpdater, stateTrie);

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

  private void updateTheAccounts(
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleWorldStateUpdateAccumulator worldStateUpdater,
      final VerkleTrie stateTrie) {
    for (final Map.Entry<Address, DiffBasedValue<VerkleAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Address accountKey = accountUpdate.getKey();
      System.out.println("account "+accountKey+" "+verkleTrieKeyValueGenerator.generateKeysForAccount(accountKey).get(0));
      final DiffBasedValue<VerkleAccount> bonsaiValue = accountUpdate.getValue();
      if (!bonsaiValue.isUnchanged()) {
        final VerkleAccount priorAccount = bonsaiValue.getPrior();
        final VerkleAccount updatedAccount = bonsaiValue.getUpdated();
        if (updatedAccount == null) {
          final Hash addressHash = hashAndSavePreImage(accountKey);
          verkleTrieKeyValueGenerator
              .generateKeysForAccount(accountKey)
              .forEach(
                  bytes -> {
                    System.out.println(
                        "remove "
                            + accountKey
                            + " "
                            + bytes
                            + " "
                            + bonsaiValue.getPrior()
                            + " "
                            + bonsaiValue.getUpdated());
                    stateTrie.remove(bytes);
                  });
          maybeStateUpdater.ifPresent(
              bonsaiUpdater -> bonsaiUpdater.removeAccountInfoState(addressHash));
        } else {
          final Bytes priorValue = priorAccount == null ? null : priorAccount.serializeAccount();
          final Bytes accountValue = updatedAccount.serializeAccount();
          if (!accountValue.equals(priorValue)) {
            verkleTrieKeyValueGenerator
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
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.putAccountInfoState(
                        hashAndSavePreImage(accountKey), accountValue));
          }
        }
      }
    }
    // TODO REMOVE THAT for next testnet (added because of a geth issue on block 4810, 5077)
    List<Address> buggyAccount = List.of(Address.fromHexString("0xb0aed5c6f925e9ed9385fd99ff2edfeedf320c6e"),
            Address.fromHexString("0x1edff765b26b0e1dc6fb41ac6638ed2a9437ec23"),
            Address.fromHexString("0x00000000000000000000000000000000000013D5"),
            Address.fromHexString("0x45173e92E492642fC0D545Fd869BDee90568aD18"),
            Address.fromHexString("0x00000000000000000000000000000000000075d0"));

    buggyAccount.forEach(address -> {
      if (worldStateUpdater
              .getAccountsToUpdate().containsKey(address)) {
        System.out.println("fix "+address);
        for (int i = 0; i < 256; i++) {
          final Bytes stem = verkleTrieKeyValueGenerator.generateKeysForAccount(address).get(0).slice(0,31);
          System.out.println("add "+Bytes.concatenate(
                  stem,
                  Bytes.of(i))+" 0x0000000000000000000000000000000000000000000000000000000000000000");
          stateTrie.put(
                  Bytes.concatenate(
                          stem,
                          Bytes.of(i)),
                  Bytes.fromHexString(
                          "0x0000000000000000000000000000000000000000000000000000000000000000"));
        }
      }
    });

  }

  private void updateCode(
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleWorldStateUpdateAccumulator worldStateUpdater) {
    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          for (final Map.Entry<Address, DiffBasedValue<Bytes>> codeUpdate :
              worldStateUpdater.getCodeToUpdate().entrySet()) {
            final Bytes previousCode = codeUpdate.getValue().getPrior();
            final Bytes updatedCode = codeUpdate.getValue().getUpdated();
            if (!codeUpdate.getValue().isUnchanged()
                && !(codeIsEmpty(previousCode) && codeIsEmpty(updatedCode))) {
              final Address address = codeUpdate.getKey();
              final Hash accountHash = address.addressHash();
              if (updatedCode == null) {
                verkleTrieKeyValueGenerator
                    .generateKeysForCode(address, previousCode)
                    .forEach(
                        bytes -> {
                          System.out.println("remove code " + bytes);
                          stateTrie.remove(bytes);
                        });
                bonsaiUpdater.removeCode(accountHash);
              } else {
                if (updatedCode.isEmpty()) {
                  verkleTrieKeyValueGenerator
                      .generateKeyValuesForCode(address, updatedCode)
                      .forEach(
                          (bytes, bytes2) -> {
                            // System.out.println("add code " + bytes + " " + bytes2);
                            stateTrie.put(bytes, bytes2);
                          });
                  bonsaiUpdater.removeCode(accountHash);
                } else {
                  verkleTrieKeyValueGenerator
                      .generateKeyValuesForCode(address, updatedCode)
                      .forEach(
                          (bytes, bytes2) -> {
                            System.out.println("add code " + bytes + " " + bytes2);
                            stateTrie.put(bytes, bytes2);
                          });
                  bonsaiUpdater.putCode(accountHash, null, updatedCode);
                }
              }
            }
          }
        });
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  private void updateAccountStorageState(
      final VerkleTrie stateTrie,
      final Optional<VerkleWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final VerkleWorldStateUpdateAccumulator worldStateUpdater,
      final Map.Entry<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
          storageAccountUpdate) {
    final Address updatedAddress = storageAccountUpdate.getKey();
    final Hash updatedAddressHash = updatedAddress.addressHash();
    if (worldStateUpdater.getAccountsToUpdate().containsKey(updatedAddress)) {

      // for manicured tries and composting, collect branches here (not implemented)
      for (final Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>> storageUpdate :
          storageAccountUpdate.getValue().entrySet()) {
        final Hash slotHash = storageUpdate.getKey().getSlotHash();

        if (!storageUpdate.getValue().isUnchanged()) {
          final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
          if (updatedStorage == null) {
            verkleTrieKeyValueGenerator
                .generateKeysForStorage(updatedAddress, storageUpdate.getKey())
                .forEach(
                    bytes -> {
                      System.out.println("remove storage" + bytes);
                      stateTrie.remove(bytes);
                    });
            maybeStateUpdater.ifPresent(
                diffBasedUpdater ->
                    diffBasedUpdater.removeStorageValueBySlotHash(updatedAddressHash, slotHash));
          } else {
            final Pair<Bytes, Bytes> storage =
                verkleTrieKeyValueGenerator.generateKeyValuesForStorage(
                    updatedAddress, storageUpdate.getKey(), updatedStorage);
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
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.putStorageValueBySlotHash(
                        updatedAddressHash, slotHash, updatedStorage));
          }
        }
      }
    }
  }

  @Override
  public MutableWorldState freeze() {
    this.isFrozen = true;
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
