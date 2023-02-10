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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiInMemoryWorldState extends BonsaiPersistedWorldState
    implements BonsaiStorageSubscriber {

  private boolean isPersisted = false;
  private final Long worldstateSubcriberId;

  public BonsaiInMemoryWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    super(archive, worldStateStorage);
    worldstateSubcriberId = worldStateStorage.subscribe(this);
  }

  @Override
  public Hash rootHash() {
    if (isPersisted) {
      return worldStateRootHash;
    }
    return rootHash(updater.copy());
  }

  public Hash rootHash(final BonsaiWorldStateUpdater localUpdater) {
    final Hash calculatedRootHash = calculateRootHash(localUpdater);
    return Hash.wrap(calculatedRootHash);
  }

  protected Hash calculateRootHash(final BonsaiWorldStateUpdater worldStateUpdater) {

    // second update account storage state.  This must be done before updating the accounts so
    // that we can get the storage state hash

    worldStateUpdater.getStorageToUpdate().entrySet().parallelStream()
        .forEach(
            addressMapEntry -> {
              updateAccountStorage(worldStateUpdater, addressMapEntry);
            });

    // next walk the account trie
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                archive
                    .getCachedMerkleTrieLoader()
                    .getAccountStateTrieNode(worldStateStorage, location, hash),
            worldStateRootHash,
            Function.identity(),
            Function.identity());

    // for manicured tries and composting, collect branches here (not implemented)

    // now add the accounts
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Bytes accountKey = accountUpdate.getKey();
      final BonsaiValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
      final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
      try {
        if (updatedAccount == null) {
          final Hash addressHash = Hash.hash(accountKey);
          accountTrie.remove(addressHash);
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
          final Bytes accountValue = updatedAccount.serializeAccount();
          accountTrie.put(addressHash, accountValue);
        }
      } catch (MerkleTrieException e) {
        // need to throw to trigger the heal
        throw new MerkleTrieException(
            e.getMessage(), Optional.of(Address.wrap(accountKey)), e.getHash(), e.getLocation());
      }
    }

    // TODO write to a cache and then generate a layer update from that and the
    // DB tx updates.  Right now it is just DB updates.
    return Hash.wrap(accountTrie.getRootHash());
  }

  private void updateAccountStorage(
      final BonsaiWorldStateUpdater worldStateUpdater,
      final Map.Entry<Address, BonsaiWorldStateUpdater.StorageConsumingMap<BonsaiValue<UInt256>>>
          storageAccountUpdate) {
    final Address updatedAddress = storageAccountUpdate.getKey();
    final Hash updatedAddressHash = Hash.hash(updatedAddress);
    if (worldStateUpdater.getAccountsToUpdate().containsKey(updatedAddress)) {
      final BonsaiValue<BonsaiAccount> accountValue =
          worldStateUpdater.getAccountsToUpdate().get(updatedAddress);
      final BonsaiAccount accountOriginal = accountValue.getPrior();
      final Hash storageRoot =
          (accountOriginal == null) ? Hash.EMPTY_TRIE_HASH : accountOriginal.getStorageRoot();

      final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
          new StoredMerklePatriciaTrie<>(
              (location, key) ->
                  archive
                      .getCachedMerkleTrieLoader()
                      .getAccountStorageTrieNode(
                          worldStateStorage, updatedAddressHash, location, key),
              storageRoot,
              Function.identity(),
              Function.identity());

      // for manicured tries and composting, collect branches here (not implemented)

      for (final Map.Entry<Hash, BonsaiValue<UInt256>> storageUpdate :
          storageAccountUpdate.getValue().entrySet()) {
        final Hash keyHash = storageUpdate.getKey();
        final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
        try {
          if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
            storageTrie.remove(keyHash);
          } else {
            storageTrie.put(keyHash, BonsaiWorldView.encodeTrieValue(updatedStorage));
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
        final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
        accountUpdated.setStorageRoot(newStorageRoot);
      }
    }
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    final BonsaiWorldStateUpdater localUpdater = updater.copy();
    final Hash newWorldStateRootHash = rootHash(localUpdater);
    archive
        .getTrieLogManager()
        .saveTrieLog(
            archive,
            localUpdater,
            newWorldStateRootHash,
            blockHeader,
            this);
    worldStateRootHash = newWorldStateRootHash;
    worldStateBlockHash = blockHeader.getBlockHash();
    isPersisted = true;
  }

  @Override
  public void close() throws Exception {
    // if storage is snapshot-based we need to close:
    worldStateStorage.unSubscribe(worldstateSubcriberId);
    worldStateStorage.close();
  }
}
