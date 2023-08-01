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

import static org.hyperledger.besu.ethereum.bonsai.BonsaiAccount.fromRLP;
import static org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator.StorageConsumingMap;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiWorldState
    implements MutableWorldState, BonsaiWorldView, BonsaiStorageSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldState.class);

  public BonsaiWorldStateKeyValueStorage worldStateStorage;

  private final BonsaiWorldStateProvider archive;
  private final BonsaiWorldStateUpdateAccumulator accumulator;

  public Hash worldStateRootHash;
  public Hash worldStateBlockHash;

  private boolean isFrozen;

  public BonsaiWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    this.archive = archive;
    this.worldStateStorage = worldStateStorage;
    worldStateRootHash =
        Hash.wrap(
            Bytes32.wrap(worldStateStorage.getWorldStateRootHash().orElse(Hash.EMPTY_TRIE_HASH)));
    worldStateBlockHash =
        Hash.wrap(Bytes32.wrap(worldStateStorage.getWorldStateBlockHash().orElse(Hash.ZERO)));
    accumulator =
        new BonsaiWorldStateUpdateAccumulator(
            this,
            (addr, value) ->
                archive
                    .getCachedMerkleTrieLoader()
                    .preLoadAccount(getWorldStateStorage(), worldStateRootHash, addr),
            (addr, value) ->
                archive
                    .getCachedMerkleTrieLoader()
                    .preLoadStorageSlot(getWorldStateStorage(), addr, value));
  }

  public BonsaiWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final BonsaiWorldStateUpdateAccumulator updater) {
    this.archive = archive;
    this.worldStateStorage = worldStateStorage;
    this.worldStateRootHash =
        Hash.wrap(
            Bytes32.wrap(worldStateStorage.getWorldStateRootHash().orElse(Hash.EMPTY_TRIE_HASH)));
    this.worldStateBlockHash =
        Hash.wrap(Bytes32.wrap(worldStateStorage.getWorldStateBlockHash().orElse(Hash.ZERO)));
    this.accumulator = updater;
  }

  public BonsaiWorldStateProvider getArchive() {
    return archive;
  }

  @Override
  public boolean isPersisted() {
    return isPersisted(worldStateStorage);
  }

  private boolean isPersisted(final WorldStateStorage worldStateStorage) {
    return !(worldStateStorage instanceof BonsaiSnapshotWorldStateKeyValueStorage);
  }

  @Override
  public Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash) {
    return worldStateStorage.getCode(codeHash, Hash.hash(address));
  }

  /**
   * Reset the worldState to this block header
   *
   * @param blockHeader block to use
   */
  public void resetWorldStateTo(final BlockHeader blockHeader) {
    worldStateBlockHash = blockHeader.getBlockHash();
    worldStateRootHash = blockHeader.getStateRoot();
  }

  @Override
  public BonsaiWorldStateKeyValueStorage getWorldStateStorage() {
    return worldStateStorage;
  }

  private Hash calculateRootHash(
      final Optional<BonsaiWorldStateKeyValueStorage.BonsaiUpdater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {

    clearStorage(maybeStateUpdater, worldStateUpdater);

    // This must be done before updating the accounts so
    // that we can get the storage state hash
    Stream<Map.Entry<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>>
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
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        createTrie(
            (location, hash) ->
                archive
                    .getCachedMerkleTrieLoader()
                    .getAccountStateTrieNode(worldStateStorage, location, hash),
            worldStateRootHash);

    // for manicured tries and composting, collect branches here (not implemented)
    updateTheAccounts(maybeStateUpdater, worldStateUpdater, accountTrie);

    // TODO write to a cache and then generate a layer update from that and the
    // DB tx updates.  Right now it is just DB updates.
    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          accountTrie.commit(
              (location, hash, value) ->
                  writeTrieNode(
                      TRIE_BRANCH_STORAGE,
                      bonsaiUpdater.getWorldStateTransaction(),
                      location,
                      value));
        });
    final Bytes32 rootHash = accountTrie.getRootHash();
    return Hash.wrap(rootHash);
  }

  private void updateTheAccounts(
      final Optional<BonsaiWorldStateKeyValueStorage.BonsaiUpdater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie) {
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
        worldStateUpdater.getAccountsToUpdate().entrySet()) {
      final Bytes accountKey = accountUpdate.getKey();
      final BonsaiValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
      final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
      try {
        if (updatedAccount == null) {
          final Hash addressHash = Hash.hash(accountKey);
          accountTrie.remove(addressHash);
          maybeStateUpdater.ifPresent(
              bonsaiUpdater -> bonsaiUpdater.removeAccountInfoState(addressHash));
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
          final Bytes accountValue = updatedAccount.serializeAccount();
          maybeStateUpdater.ifPresent(
              bonsaiUpdater ->
                  bonsaiUpdater.putAccountInfoState(Hash.hash(accountKey), accountValue));
          accountTrie.put(addressHash, accountValue);
        }
      } catch (MerkleTrieException e) {
        // need to throw to trigger the heal
        throw new MerkleTrieException(
            e.getMessage(), Optional.of(Address.wrap(accountKey)), e.getHash(), e.getLocation());
      }
    }
  }

  private void updateCode(
      final Optional<BonsaiWorldStateKeyValueStorage.BonsaiUpdater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {
    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate :
              worldStateUpdater.getCodeToUpdate().entrySet()) {
            final Bytes updatedCode = codeUpdate.getValue().getUpdated();
            final Hash accountHash = Hash.hash(codeUpdate.getKey());
            if (updatedCode == null || updatedCode.size() == 0) {
              bonsaiUpdater.removeCode(accountHash);
            } else {
              bonsaiUpdater.putCode(accountHash, null, updatedCode);
            }
          }
        });
  }

  private void updateAccountStorageState(
      final Optional<BonsaiWorldStateKeyValueStorage.BonsaiUpdater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater,
      final Map.Entry<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>
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
          createTrie(
              (location, key) ->
                  archive
                      .getCachedMerkleTrieLoader()
                      .getAccountStorageTrieNode(
                          worldStateStorage, updatedAddressHash, location, key),
              storageRoot);

      // for manicured tries and composting, collect branches here (not implemented)
      for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> storageUpdate :
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
            storageTrie.put(slotHash, BonsaiWorldView.encodeTrieValue(updatedStorage));
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
            bonsaiUpdater -> {
              storageTrie.commit(
                  (location, key, value) ->
                      writeStorageTrieNode(
                          bonsaiUpdater, updatedAddressHash, location, key, value));
            });
        final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
        accountUpdated.setStorageRoot(newStorageRoot);
      }
    }
    // for manicured tries and composting, trim and compost here
  }

  private void clearStorage(
      final Optional<BonsaiWorldStateKeyValueStorage.BonsaiUpdater> maybeStateUpdater,
      final BonsaiWorldStateUpdateAccumulator worldStateUpdater) {

    maybeStateUpdater.ifPresent(
        bonsaiUpdater -> {
          for (final Address address : worldStateUpdater.getStorageToClear()) {
            // because we are clearing persisted values we need the account root as persisted
            final BonsaiAccount oldAccount =
                worldStateStorage
                    .getAccount(Hash.hash(address))
                    .map(bytes -> fromRLP(BonsaiWorldState.this, address, bytes, true))
                    .orElse(null);
            if (oldAccount == null) {
              // This is when an account is both created and deleted within the scope of the same
              // block.  A not-uncommon DeFi bot pattern.
              continue;
            }
            final Hash addressHash = Hash.hash(address);
            final MerkleTrie<Bytes, Bytes> storageTrie =
                createTrie(
                    (location, key) -> getStorageTrieNode(addressHash, location, key),
                    oldAccount.getStorageRoot());
            try {
              Map<Bytes32, Bytes> entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
              while (!entriesToDelete.isEmpty()) {
                entriesToDelete
                    .keySet()
                    .forEach(
                        k ->
                            bonsaiUpdater.removeStorageValueBySlotHash(
                                Hash.hash(address), Hash.wrap(k)));
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
        });
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    final Optional<BlockHeader> maybeBlockHeader = Optional.ofNullable(blockHeader);
    LOG.atDebug()
        .setMessage("Persist world state for block {}")
        .addArgument(maybeBlockHeader)
        .log();

    final BonsaiWorldStateUpdateAccumulator localCopy = accumulator.copy();

    boolean success = false;

    final BonsaiWorldStateKeyValueStorage.BonsaiUpdater stateUpdater = worldStateStorage.updater();
    Runnable saveTrieLog = () -> {};

    try {
      final Hash newWorldStateRootHash =
          calculateRootHash(isFrozen ? Optional.empty() : Optional.of(stateUpdater), accumulator);
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
        saveTrieLog =
            () -> {
              final TrieLogManager trieLogManager = archive.getTrieLogManager();
              trieLogManager.saveTrieLog(localCopy, newWorldStateRootHash, blockHeader, this);
              // not save a frozen state in the cache
              if (!isFrozen) {
                trieLogManager.addCachedLayer(blockHeader, newWorldStateRootHash, this);
              }
            };

        stateUpdater
            .getWorldStateTransaction()
            .put(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHeader.getHash().toArrayUnsafe());
        worldStateBlockHash = blockHeader.getHash();
      } else {
        stateUpdater.getWorldStateTransaction().remove(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY);
        worldStateBlockHash = null;
      }

      stateUpdater
          .getWorldStateTransaction()
          .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, newWorldStateRootHash.toArrayUnsafe());
      worldStateRootHash = newWorldStateRootHash;
      success = true;
    } finally {
      if (success) {
        stateUpdater.commit();
        accumulator.reset();
        saveTrieLog.run();
      } else {
        stateUpdater.rollback();
        accumulator.reset();
      }
    }
  }

  @Override
  public WorldUpdater updater() {
    return accumulator;
  }

  @Override
  public Hash rootHash() {
    if (isFrozen && accumulator.isAccumulatorStateChanged()) {
      worldStateRootHash = calculateRootHash(Optional.empty(), accumulator.copy());
      accumulator.resetAccumulatorStateChanged();
    }
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

  static final SegmentedKeyValueStorageTransaction noOpSegmentedTx =
      new SegmentedKeyValueStorageTransaction() {

        @Override
        public void put(
            final SegmentIdentifier segmentIdentifier, final byte[] key, final byte[] value) {
          // no-op
        }

        @Override
        public void remove(final SegmentIdentifier segmentIdentifier, final byte[] key) {
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
        Optional.of(new BonsaiWorldStateKeyValueStorage.Updater(noOpSegmentedTx, noOpTx)),
        accumulator.copy());
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
        .map(bytes -> fromRLP(accumulator, address, bytes, true))
        .orElse(null);
  }

  protected Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return worldStateStorage.getAccountStateTrieNode(location, nodeHash);
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
  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValueByStorageSlotKey(address, new StorageSlotKey(storageKey))
        .orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    return worldStateStorage
        .getStorageValueByStorageSlotKey(Hash.hash(address), storageSlotKey)
        .map(UInt256::fromBytes);
  }

  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Address address,
      final StorageSlotKey storageSlotKey) {
    return worldStateStorage
        .getStorageValueByStorageSlotKey(storageRootSupplier, Hash.hash(address), storageSlotKey)
        .map(UInt256::fromBytes);
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        createTrie(
            (location, key) -> getStorageTrieNode(Hash.hash(address), location, key), rootHash);
    return storageTrie.entriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
  }

  @Override
  public MutableWorldState freeze() {
    this.isFrozen = true;
    this.worldStateStorage = new BonsaiWorldStateLayerStorage(worldStateStorage);
    return this;
  }

  private StoredMerklePatriciaTrie<Bytes, Bytes> createTrie(
      final NodeLoader nodeLoader, final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        nodeLoader, rootHash, Function.identity(), Function.identity());
  }

  @Override
  public void close() {
    try {
      if (!isPersisted()) {
        this.worldStateStorage.close();
        if (isFrozen) {
          closeFrozenStorage();
        }
      }
    } catch (Exception e) {
      // no op
    }
  }

  private void closeFrozenStorage() {
    try {
      final BonsaiWorldStateLayerStorage worldStateLayerStorage =
          (BonsaiWorldStateLayerStorage) worldStateStorage;
      if (!isPersisted(worldStateLayerStorage.getParentWorldStateStorage())) {
        worldStateLayerStorage.getParentWorldStateStorage().close();
      }
    } catch (Exception e) {
      // no op
    }
  }
}
