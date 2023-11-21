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

package org.hyperledger.besu.ethereum.bonsai.worldview;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrieKeyValueGenerator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiVerkleWorldState extends BonsaiWorldState {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiVerkleWorldState.class);
  private final VerkleTrieKeyValueGenerator verkleTrieKeyValueGenerator =
      new VerkleTrieKeyValueGenerator();

  public BonsaiVerkleWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final EvmConfiguration evmConfiguration) {
    super(archive, worldStateStorage, evmConfiguration);
  }

  @Override
  protected Hash getEmptyTrieHash() {
    return Hash.wrap(Bytes32.ZERO);
  }

  @Override
  protected Hash calculateRootHash(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {

    final VerkleTrie stateTrie =
        createTrie(
            (location, hash) -> worldStateStorage.getTrieNodeUnsafe(location), worldStateRootHash);
    // clearStorage(maybeStateUpdater, worldStateUpdater);

    Stream<Map.Entry<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>>
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
    final Bytes32 rootHash = stateTrie.getRootHash();

    LOG.info("end commit ");
    return Hash.wrap(rootHash);
  }

  private void updateTheAccounts(
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final VerkleTrie stateTrie) {
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Address accountKey = accountUpdate.getKey();
      final BonsaiValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
      final BonsaiAccount priorAccount = bonsaiValue.getPrior();
      final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
      if (updatedAccount == null) {
        final Hash addressHash = hashAndSavePreImage(accountKey);
        verkleTrieKeyValueGenerator
            .generateKeysForAccount(accountKey)
            .forEach(
                bytes -> {
                  System.out.println("remove " + bytes);
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
                  accountKey, updatedAccount.getNonce(), updatedAccount.getBalance())
              .forEach(
                  (bytes, bytes2) -> {
                    System.out.println("add " + bytes + " " + bytes2);
                    stateTrie.put(bytes, bytes2);
                  });
          maybeStateUpdater.ifPresent(
              bonsaiUpdater ->
                  bonsaiUpdater.putAccountInfoState(hashAndSavePreImage(accountKey), accountValue));
        }
      }
    }
  }

  private void updateCode(
      final VerkleTrie stateTrie,
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {
    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate :
              worldStateUpdater.getCodeToUpdate().entrySet()) {
            final Bytes previousCode = codeUpdate.getValue().getPrior();
            final Bytes updatedCode = codeUpdate.getValue().getUpdated();
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
                final Hash codeHash = updatedCode.size() == 0 ? Hash.EMPTY : Hash.hash(updatedCode);
                verkleTrieKeyValueGenerator
                    .generateKeyValuesForCode(address, codeHash, updatedCode)
                    .forEach(
                        (bytes, bytes2) -> {
                          // System.out.println("add code " + bytes + " " + bytes2);
                          stateTrie.put(bytes, bytes2);
                        });
                bonsaiUpdater.removeCode(accountHash);
              } else {
                final Hash codeHash = updatedCode.size() == 0 ? Hash.EMPTY : Hash.hash(updatedCode);
                verkleTrieKeyValueGenerator
                    .generateKeyValuesForCode(address, codeHash, updatedCode)
                    .forEach(
                        (bytes, bytes2) -> {
                          System.out.println("add code " + bytes + " " + bytes2);
                          stateTrie.put(bytes, bytes2);
                        });
                bonsaiUpdater.putCode(accountHash, null, updatedCode);
              }
            }
          }
        });
  }

  private void updateAccountStorageState(
      final VerkleTrie stateTrie,
      final Optional<BonsaiWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final Map.Entry<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>
          storageAccountUpdate) {
    final Address updatedAddress = storageAccountUpdate.getKey();
    final Hash updatedAddressHash = updatedAddress.addressHash();
    if (worldStateUpdater.getAccountsToUpdate().containsKey(updatedAddress)) {

      // for manicured tries and composting, collect branches here (not implemented)
      for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> storageUpdate :
          storageAccountUpdate.getValue().entrySet()) {
        final Hash slotHash = storageUpdate.getKey().getSlotHash();
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
              bonsaiUpdater ->
                  bonsaiUpdater.removeStorageValueBySlotHash(updatedAddressHash, slotHash));
        } else {
          final Pair<Bytes, Bytes> storage =
              verkleTrieKeyValueGenerator.generateKeyValuesForStorage(
                  updatedAddress, storageUpdate.getKey(), updatedStorage);
          System.out.println("add storage " + storage.getFirst() + " " + storage.getSecond());
          stateTrie
              .put(storage.getFirst(), storage.getSecond())
              .ifPresentOrElse(
                  bytes -> {
                    System.out.println("found old key " + bytes);
                    storageUpdate.getValue().setPrior(UInt256.fromBytes(bytes));
                  },
                  () -> {
                    storageUpdate.getValue().setPrior(null);
                  });
          if (updatedStorage.equals(UInt256.ZERO)) {
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.removeStorageValueBySlotHash(updatedAddressHash, slotHash));
          } else {
            maybeStateUpdater.ifPresent(
                bonsaiUpdater ->
                    bonsaiUpdater.putStorageValueBySlotHash(
                        updatedAddressHash, slotHash, updatedStorage));
          }
        }
      }
    }
  }

  private VerkleTrie createTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    return new VerkleTrie(nodeLoader, rootHash);
  }
}
