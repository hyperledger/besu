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
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiPersistedWorldState implements MutableWorldState, BonsaiWorldView {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiPersistedWorldState.class);

  protected final BonsaiWorldStateKeyValueStorage worldStateStorage;

  protected final BonsaiWorldStateArchive archive;
  protected final BonsaiWorldStateUpdater updater;

  protected Hash worldStateRootHash;
  protected Hash worldStateBlockHash;

  public BonsaiPersistedWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    this.archive = archive;
    this.worldStateStorage = worldStateStorage;
    worldStateRootHash =
        Hash.wrap(
            Bytes32.wrap(worldStateStorage.getWorldStateRootHash().orElse(Hash.EMPTY_TRIE_HASH)));
    worldStateBlockHash =
        Hash.wrap(Bytes32.wrap(worldStateStorage.getWorldStateBlockHash().orElse(Hash.ZERO)));
    updater = new BonsaiWorldStateUpdater(this);
  }

  public BonsaiWorldStateArchive getArchive() {
    return archive;
  }

  @Override
  public MutableWorldState copy() {
    return new BonsaiInMemoryWorldState(
        archive,
        new BonsaiInMemoryWorldStateKeyValueStorage(
            worldStateStorage.accountStorage,
            worldStateStorage.codeStorage,
            worldStateStorage.storageStorage,
            worldStateStorage.trieBranchStorage,
            worldStateStorage.trieLogStorage));
  }

  @Override
  public Optional<Bytes> getCode(@Nonnull final Address address) {
    return worldStateStorage.getCode(null, Hash.hash(address));
  }

  public void setArchiveStateUnSafe(final BlockHeader blockHeader) {
    worldStateBlockHash = Hash.fromPlugin(blockHeader.getBlockHash());
    worldStateRootHash = Hash.fromPlugin(blockHeader.getStateRoot());
  }

  public BonsaiWorldStateKeyValueStorage getWorldStateStorage() {
    return worldStateStorage;
  }

  protected Hash calculateRootHash(
      final BonsaiWorldStateKeyValueStorage.Updater stateUpdater,
      final BonsaiWorldStateUpdater worldStateUpdater) {
    // first clear storage
    for (final Address address : worldStateUpdater.getStorageToClear()) {
      // because we are clearing persisted values we need the account root as persisted
      final BonsaiAccount oldAccount =
          worldStateStorage
              .getAccount(Hash.hash(address))
              .map(bytes -> fromRLP(BonsaiPersistedWorldState.this, address, bytes, true))
              .orElse(null);
      if (oldAccount == null) {
        // This is when an account is both created and deleted within the scope of the same
        // block.  A not-uncommon DeFi bot pattern.
        continue;
      }
      final Hash addressHash = Hash.hash(address);
      final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
          new StoredMerklePatriciaTrie<>(
              (location, key) -> getStorageTrieNode(addressHash, location, key),
              oldAccount.getStorageRoot(),
              Function.identity(),
              Function.identity());
      Map<Bytes32, Bytes> entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
      while (!entriesToDelete.isEmpty()) {
        entriesToDelete
            .keySet()
            .forEach(
                k -> stateUpdater.removeStorageValueBySlotHash(Hash.hash(address), Hash.wrap(k)));
        if (entriesToDelete.size() == 256) {
          entriesToDelete.keySet().forEach(storageTrie::remove);
          entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
        } else {
          break;
        }
      }
    }

    // second update account storage state.  This must be done before updating the accounts so
    // that we can get the storage state hash
    for (final Map.Entry<Address, Map<Hash, BonsaiValue<UInt256>>> storageAccountUpdate :
        worldStateUpdater.getStorageToUpdate().entrySet()) {
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
                (location, key) -> getStorageTrieNode(updatedAddressHash, location, key),
                storageRoot,
                Function.identity(),
                Function.identity());

        // for manicured tries and composting, collect branches here (not implemented)

        for (final Map.Entry<Hash, BonsaiValue<UInt256>> storageUpdate :
            storageAccountUpdate.getValue().entrySet()) {
          final Hash keyHash = storageUpdate.getKey();
          final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
          if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
            stateUpdater.removeStorageValueBySlotHash(updatedAddressHash, keyHash);
            storageTrie.remove(keyHash);
          } else {
            stateUpdater.putStorageValueBySlotHash(updatedAddressHash, keyHash, updatedStorage);
            storageTrie.put(keyHash, BonsaiWorldView.encodeTrieValue(updatedStorage));
          }
        }

        final BonsaiAccount accountUpdated = accountValue.getUpdated();
        if (accountUpdated != null) {
          storageTrie.commit(
              (location, key, value) ->
                  writeStorageTrieNode(stateUpdater, updatedAddressHash, location, key, value));
          final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
          accountUpdated.setStorageRoot(newStorageRoot);
        }
      }
      // for manicured tries and composting, trim and compost here
    }

    // Third update the code.  This has the side effect of ensuring a code hash is calculated.
    for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate :
        worldStateUpdater.getCodeToUpdate().entrySet()) {
      final Bytes updatedCode = codeUpdate.getValue().getUpdated();
      final Hash accountHash = Hash.hash(codeUpdate.getKey());
      if (updatedCode == null || updatedCode.size() == 0) {
        stateUpdater.removeCode(accountHash);
      } else {
        stateUpdater.putCode(accountHash, null, updatedCode);
      }
    }

    // next walk the account trie
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            this::getAccountStateTrieNode,
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
      if (updatedAccount == null) {
        final Hash addressHash = Hash.hash(accountKey);
        accountTrie.remove(addressHash);
        stateUpdater.removeAccountInfoState(addressHash);
      } else {
        final Hash addressHash = updatedAccount.getAddressHash();
        final Bytes accountValue = updatedAccount.serializeAccount();
        stateUpdater.putAccountInfoState(Hash.hash(accountKey), accountValue);
        accountTrie.put(addressHash, accountValue);
      }
    }

    // TODO write to a cache and then generate a layer update from that and the
    // DB tx updates.  Right now it is just DB updates.
    accountTrie.commit(
        (location, hash, value) ->
            writeTrieNode(stateUpdater.getTrieBranchStorageTransaction(), location, value));
    final Bytes32 rootHash = accountTrie.getRootHash();
    return Hash.wrap(rootHash);
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    final Optional<BlockHeader> maybeBlockHeader = Optional.ofNullable(blockHeader);
    debugLambda(LOG, "Persist world state for block {}", maybeBlockHeader::toString);
    boolean success = false;

    final BonsaiWorldStateUpdater localUpdater = updater.copy();
    final BonsaiWorldStateKeyValueStorage.Updater stateUpdater = worldStateStorage.updater();

    try {
      final Hash newWorldStateRootHash = calculateRootHash(stateUpdater, localUpdater);
      // if we are persisted with a block header, and the prior state is the parent
      // then persist the TrieLog for that transition.
      // If specified but not a direct descendant simply store the new block hash.
      if (blockHeader != null) {
        if (!newWorldStateRootHash.equals(blockHeader.getStateRoot())) {
          throw new RuntimeException(
              "World State Root does not match expected value, header "
                  + blockHeader.getStateRoot().toHexString()
                  + " calculated "
                  + newWorldStateRootHash.toHexString());
        }
        archive
            .getTrieLogManager()
            .saveTrieLog(archive, localUpdater, newWorldStateRootHash, blockHeader);
        stateUpdater
            .getTrieBranchStorageTransaction()
            .put(WORLD_BLOCK_HASH_KEY, blockHeader.getHash().toArrayUnsafe());
        worldStateBlockHash = blockHeader.getHash();
      } else {
        stateUpdater.getTrieBranchStorageTransaction().remove(WORLD_BLOCK_HASH_KEY);
        worldStateBlockHash = null;
      }

      stateUpdater
          .getTrieBranchStorageTransaction()
          .put(WORLD_ROOT_HASH_KEY, newWorldStateRootHash.toArrayUnsafe());
      worldStateRootHash = newWorldStateRootHash;
      success = true;
    } finally {
      if (success) {
        stateUpdater.commit();
        updater.reset();
      } else {
        stateUpdater.rollback();
        updater.reset();
      }
    }
  }

  @Override
  public WorldUpdater updater() {
    return updater;
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(worldStateRootHash);
  }

  static final KeyValueStorageTransaction noOpTx =
      new KeyValueStorageTransaction() {

        @Override
        public void put(final byte[] key, final byte[] value) {
          // no-op
        }

        @Override
        public void remove(final byte[] key) {
          // no-op
        }

        @Override
        public void commit() throws StorageException {
          // no-op
        }

        @Override
        public void rollback() {
          // no-op
        }
      };

  @Override
  public Hash frontierRootHash() {
    return calculateRootHash(
        new BonsaiWorldStateKeyValueStorage.Updater(noOpTx, noOpTx, noOpTx, noOpTx, noOpTx),
        updater.copy());
  }

  public Hash blockHash() {
    return worldStateBlockHash;
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries do not provide account streaming.");
  }

  @Override
  public Account get(final Address address) {
    return worldStateStorage
        .getAccount(Hash.hash(address))
        .map(bytes -> fromRLP(updater, address, bytes, true))
        .orElse(null);
  }

  private Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return worldStateStorage.getAccountStateTrieNode(location, nodeHash);
  }

  private void writeTrieNode(
      final KeyValueStorageTransaction tx, final Bytes location, final Bytes value) {
    tx.put(location.toArrayUnsafe(), value.toArrayUnsafe());
  }

  private Optional<Bytes> getStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return worldStateStorage.getAccountStorageTrieNode(accountHash, location, nodeHash);
  }

  private void writeStorageTrieNode(
      final WorldStateStorage.Updater stateUpdater,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash,
      final Bytes value) {
    stateUpdater.putAccountStorageTrieNode(accountHash, location, nodeHash, value);
  }

  @Override
  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    return worldStateStorage.getStateTrieNode(location);
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValueBySlotHash(address, Hash.hash(storageKey)).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    return worldStateStorage
        .getStorageValueBySlotHash(Hash.hash(address), slotHash)
        .map(UInt256::fromBytes);
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, key) -> getStorageTrieNode(Hash.hash(address), location, key),
            rootHash,
            Function.identity(),
            Function.identity());
    return storageTrie.entriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
  }
}
