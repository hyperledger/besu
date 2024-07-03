/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.common.worldview;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.diffbased.common.cache.DiffBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DiffBasedWorldState
    implements MutableWorldState, DiffBasedWorldView, StorageSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(DiffBasedWorldState.class);

  protected DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage;
  protected final DiffBasedCachedWorldStorageManager cachedWorldStorageManager;
  protected final TrieLogManager trieLogManager;
  protected DiffBasedWorldStateUpdateAccumulator<?> accumulator;

  protected Hash worldStateRootHash;
  protected Hash worldStateBlockHash;
  protected DiffBasedWorldStateConfig worldStateConfig;

  protected DiffBasedWorldState(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final DiffBasedCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final DiffBasedWorldStateConfig diffBasedWorldStateConfig) {
    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    this.worldStateRootHash =
        Hash.wrap(
            Bytes32.wrap(
                worldStateKeyValueStorage.getWorldStateRootHash().orElse(getEmptyTrieHash())));
    this.worldStateBlockHash =
        Hash.wrap(
            Bytes32.wrap(worldStateKeyValueStorage.getWorldStateBlockHash().orElse(Hash.ZERO)));
    this.cachedWorldStorageManager = cachedWorldStorageManager;
    this.trieLogManager = trieLogManager;
    this.worldStateConfig = diffBasedWorldStateConfig;
  }

  /**
   * Having a protected method to override the accumulator solves the chicken-egg problem of needing
   * a worldstate reference (this) when constructing the Accumulator.
   *
   * @param accumulator accumulator to use.
   */
  public void setAccumulator(final DiffBasedWorldStateUpdateAccumulator<?> accumulator) {
    this.accumulator = accumulator;
  }

  /**
   * Returns the world state block hash of this world state
   *
   * @return the world state block hash.
   */
  public Hash getWorldStateBlockHash() {
    return worldStateBlockHash;
  }

  /**
   * Returns the world state root hash of this world state
   *
   * @return the world state root hash.
   */
  public Hash getWorldStateRootHash() {
    return worldStateRootHash;
  }

  @Override
  public boolean isPersisted() {
    return isPersisted(worldStateKeyValueStorage);
  }

  private boolean isPersisted(final WorldStateKeyValueStorage worldStateKeyValueStorage) {
    return !(worldStateKeyValueStorage instanceof DiffBasedSnapshotWorldStateKeyValueStorage);
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
  public DiffBasedWorldStateKeyValueStorage getWorldStateStorage() {
    return worldStateKeyValueStorage;
  }

  public DiffBasedWorldStateUpdateAccumulator<?> getAccumulator() {
    return accumulator;
  }

  protected Hash unsafeRootHashUpdate(
      final BlockHeader blockHeader,
      final DiffBasedWorldStateKeyValueStorage.Updater stateUpdater) {
    // calling calculateRootHash in order to update the state
    calculateRootHash(
        worldStateConfig.isFrozen() ? Optional.empty() : Optional.of(stateUpdater), accumulator);
    return blockHeader.getStateRoot();
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    final Optional<BlockHeader> maybeBlockHeader = Optional.ofNullable(blockHeader);
    LOG.atDebug()
        .setMessage("Persist world state for block {}")
        .addArgument(maybeBlockHeader)
        .log();

    final DiffBasedWorldStateUpdateAccumulator<?> localCopy = accumulator.copy();

    boolean success = false;

    final DiffBasedWorldStateKeyValueStorage.Updater stateUpdater =
        worldStateKeyValueStorage.updater();
    Runnable saveTrieLog = () -> {};

    try {
      final Hash calculatedRootHash;

      if (blockHeader == null || !worldStateConfig.isTrieDisabled()) {
        calculatedRootHash =
            calculateRootHash(
                worldStateConfig.isFrozen() ? Optional.empty() : Optional.of(stateUpdater),
                accumulator);
      } else {
        // if the trie is disabled, we cannot calculate the state root, so we directly use the root
        // of the block. It's important to understand that in all networks,
        // the state root must be validated independently and the block should not be trusted
        // implicitly. This mode
        // can be used in cases where Besu would just be a follower of another trusted client.
        calculatedRootHash = unsafeRootHashUpdate(blockHeader, stateUpdater);
      }
      // if we are persisted with a block header, and the prior state is the parent
      // then persist the TrieLog for that transition.
      // If specified but not a direct descendant simply store the new block hash.
      if (blockHeader != null) {
        verifyWorldStateRoot(calculatedRootHash, blockHeader);
        saveTrieLog =
            () -> {
              trieLogManager.saveTrieLog(localCopy, calculatedRootHash, blockHeader, this);
              // not save a frozen state in the cache
              if (!worldStateConfig.isFrozen()) {
                cachedWorldStorageManager.addCachedLayer(blockHeader, calculatedRootHash, this);
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
          .put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, calculatedRootHash.toArrayUnsafe());
      worldStateRootHash = calculatedRootHash;
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

  protected void verifyWorldStateRoot(final Hash calculatedStateRoot, final BlockHeader header) {
    if (!worldStateConfig.isTrieDisabled() && !calculatedStateRoot.equals(header.getStateRoot())) {
      throw new RuntimeException(
          "World State Root does not match expected value, header "
              + header.getStateRoot().toHexString()
              + " calculated "
              + calculatedStateRoot.toHexString());
    }
  }

  @Override
  public WorldUpdater updater() {
    return accumulator;
  }

  @Override
  public Hash rootHash() {
    if (worldStateConfig.isFrozen() && accumulator.isAccumulatorStateChanged()) {
      worldStateRootHash = calculateRootHash(Optional.empty(), accumulator.copy());
      accumulator.resetAccumulatorStateChanged();
    }
    return Hash.wrap(worldStateRootHash);
  }

  protected static final KeyValueStorageTransaction noOpTx =
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

  protected static final SegmentedKeyValueStorageTransaction noOpSegmentedTx =
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

  public Hash blockHash() {
    return worldStateBlockHash;
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("storage format do not provide account streaming.");
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public void close() {
    try {
      if (!isPersisted()) {
        this.worldStateKeyValueStorage.close();
        if (worldStateConfig.isFrozen()) {
          closeFrozenStorage();
        }
      }
    } catch (Exception e) {
      // no op
    }
  }

  private void closeFrozenStorage() {
    try {
      final DiffBasedLayeredWorldStateKeyValueStorage worldStateLayerStorage =
          (DiffBasedLayeredWorldStateKeyValueStorage) worldStateKeyValueStorage;
      if (!isPersisted(worldStateLayerStorage.getParentWorldStateStorage())) {
        worldStateLayerStorage.getParentWorldStateStorage().close();
      }
    } catch (Exception e) {
      // no op
    }
  }

  @Override
  public abstract Hash frontierRootHash();

  @Override
  public abstract MutableWorldState freeze();

  @Override
  public abstract Account get(final Address address);

  @Override
  public abstract UInt256 getStorageValue(final Address address, final UInt256 storageKey);

  @Override
  public abstract Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey);

  @Override
  public abstract Optional<Bytes> getCode(@Nonnull final Address address, final Hash codeHash);

  protected abstract Hash calculateRootHash(
      final Optional<DiffBasedWorldStateKeyValueStorage.Updater> maybeStateUpdater,
      final DiffBasedWorldStateUpdateAccumulator<?> worldStateUpdater);

  protected abstract Hash getEmptyTrieHash();
}
