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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.ARCHIVE_PROOF_BLOCK_NUMBER_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.common.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Placeholder */
public class BonsaiArchiveProofsFlatDbStrategy extends BonsaiArchiveFlatDbStrategy {

  private final Long trieNodeCheckpointInterval;

  /**
   * Placeholder
   *
   * @param metricsSystem placeholder
   * @param codeStorageStrategy placeholder
   * @param trieNodeCheckpointInterval placeholder
   */
  public BonsaiArchiveProofsFlatDbStrategy(
      final MetricsSystem metricsSystem,
      final CodeStorageStrategy codeStorageStrategy,
      final Long trieNodeCheckpointInterval) {
    super(metricsSystem, codeStorageStrategy);
    this.trieNodeCheckpointInterval = trieNodeCheckpointInterval;
  }

  private Optional<BonsaiContext> getStateTrieArchiveContextForWrite(
      final SegmentedKeyValueStorage storage) {
    // For Bonsai archive get the flat DB context to use for writing trie archive entries. We add
    // one because we're working with the latest world state so putting new flat DB keys requires us
    // to +1 to it
    Optional<byte[]> archiveContext = storage.get(TRIE_BRANCH_STORAGE, WORLD_BLOCK_NUMBER_KEY);
    if (archiveContext.isPresent()) {
      try {

        long trieContext;

        Optional<byte[]> archiveRollingContext =
            storage.get(TRIE_BRANCH_STORAGE, ARCHIVE_PROOF_BLOCK_NUMBER_KEY);
        if (archiveRollingContext.isPresent()) {
          trieContext = Bytes.wrap(archiveRollingContext.get()).toLong();
        } else {
          // MRW We're not rolling to a specific block's state trie - we need to round down to the
          // nearest checkpoint interval
          trieContext =
              (((Bytes.wrap(archiveContext.get()).toLong() + 1) / trieNodeCheckpointInterval)
                  * trieNodeCheckpointInterval);
        }

        return Optional.of(
            // The context for flat-DB PUTs is the block number recorded in the specified world
            // state, + 1
            new BonsaiContext(trieContext));
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "World state archive context invalid format: "
                + new String(archiveContext.get(), StandardCharsets.UTF_8));
      }
    } else {
      // Archive flat-db entries cannot be PUT if we don't have block context
      throw new IllegalStateException("World state missing archive context");
    }
  }

  /*
   * Retrieves the account data for the given account hash, using the world state root hash supplier and node loader.
   */
  @Override
  public Optional<Bytes> getFlatAccountTrieNode(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Bytes location,
      final Bytes32 nodeHash,
      final SegmentedKeyValueStorage storage) {
    // TODO - metrics?
    Optional<Bytes> accountFound;

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest =
        calculateArchiveKeyWithMaxSuffix(
            getStateArchiveContextForRead(storage), location.toArrayUnsafe());

    // Find the nearest account state for this address and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestAccountPreSizeCheck =
        storage
            .getNearestBeforeMatchLength(TRIE_BRANCH_STORAGE, keyNearest)
            .filter(
                found ->
                    found.key().size() == (location.size() + 8)) // TODO - change for CONST length
            .filter(found -> location.commonPrefixLength(found.key()) >= location.size());
    // .filter(found -> Hash.hash(Bytes.wrap(found.value().get())).equals(nodeHash));

    // TODO - getFlatAccount does extra checks for the delete case. Do we need to do anything in
    // that respect here?
    accountFound =
        nearestAccountPreSizeCheck.flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    return accountFound;
  }

  /*
   * Retrieves the account data for the given account hash, using the world state root hash supplier and node loader.
   */
  @Override
  public Optional<Bytes> getFlatTrieNodeUnsafe(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Bytes location,
      final SegmentedKeyValueStorage storage) {
    Optional<Bytes> accountFound;

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest =
        calculateArchiveKeyWithMaxSuffix(
            getStateArchiveContextForRead(storage), location.toArrayUnsafe());

    // MRW todo - is common prefix length check valid for state proof DB?
    // Find the nearest account state for this address and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestAccountPreSizeCheck =
        storage
            .getNearestBeforeMatchLength(TRIE_BRANCH_STORAGE, keyNearest)
            .filter(
                found ->
                    found.key().size() == (location.size() + 8)) // TODO - change for CONST length);
            .filter(found -> location.commonPrefixLength(found.key()) >= location.size());

    accountFound =
        nearestAccountPreSizeCheck.flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    return accountFound;
  }

  /*
   * Retrieves the storage value for the given storage slot hash, using the world state root hash supplier and node loader.
   */
  @Override
  public Optional<Bytes> getFlatStorageTrieNode(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash,
      final SegmentedKeyValueStorage storage) {
    // TODO - metrics?
    Optional<Bytes> storageFound;

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest =
        calculateArchiveKeyWithMaxSuffix(
            getStateArchiveContextForRead(storage),
            Bytes.concatenate(accountHash, location).toArrayUnsafe());

    // Find the nearest account state for this address and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestAccountPreSizeCheck =
        storage
            .getNearestBeforeMatchLength(TRIE_BRANCH_STORAGE, keyNearest)
            .filter(
                found ->
                    found.key().size()
                        == (accountHash.size()
                            + location.size()
                            + 8)) // TODO - change for CONST length
            .filter(
                found ->
                    Bytes.concatenate(accountHash, location).commonPrefixLength(found.key())
                        >= Bytes.concatenate(accountHash, location).size());
    // .filter(found -> Hash.hash(Bytes.wrap(found.value().get())).equals(nodeHash));

    storageFound =
        nearestAccountPreSizeCheck.flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    return storageFound;
  }

  @Override
  public void putFlatAccountTrieNode(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Bytes location,
      final Bytes32 nodeHash,
      final Bytes node) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed =
        calculateArchiveKeyWithMinSuffix(
            getStateTrieArchiveContextForWrite(storage).get(), location.toArrayUnsafe());

    transaction.put(TRIE_BRANCH_STORAGE, keySuffixed, node.toArrayUnsafe());
  }

  @Override
  public void putFlatStorageTrieNode(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash,
      final Bytes node) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed =
        calculateArchiveKeyWithMinSuffix(
            getStateTrieArchiveContextForWrite(storage).get(),
            Bytes.concatenate(accountHash, location).toArrayUnsafe());

    transaction.put(TRIE_BRANCH_STORAGE, keySuffixed, node.toArrayUnsafe());
  }
}
