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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BonsaiArchiveProofsFlatDbStrategyTest {

  private static final Long CHECKPOINT_INTERVAL = 256L;
  private BonsaiArchiveProofsFlatDbStrategy archiveProofsFlatDbStrategy;
  private SegmentedKeyValueStorage storage;

  @BeforeEach
  public void setup() {
    storage = new SegmentedInMemoryKeyValueStorage();
    archiveProofsFlatDbStrategy =
        new BonsaiArchiveProofsFlatDbStrategy(
            new NoOpMetricsSystem(),
            new CodeHashCodeStorageStrategy(),
            CHECKPOINT_INTERVAL,
            storage);
  }

  @Test
  public void genesisBlockUsesZeroSuffixWhenWorldBlockNumberKeyNotSet() {
    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0xAABBCC");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(storage, tx, location, nodeHash, nodeValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(location, Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  @Test
  public void block1UsesCheckpointIntervalSuffixWhenWorldBlockNumberKeyIsZero() {
    setWorldBlockNumber(0);

    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0xDDEEFF");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(storage, tx, location, nodeHash, nodeValue);
    tx.commit();

    // When world block number is 0, the trie context is calculated as:
    // ((0 + 1) / 256) * 256 = 0
    final byte[] expectedKey =
        Bytes.concatenate(location, Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  @Test
  public void blockAtCheckpointBoundaryUsesCorrectSuffix() {
    // Set world block number to 255 (just before first checkpoint)
    setWorldBlockNumber(255);

    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0x112233");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(storage, tx, location, nodeHash, nodeValue);
    tx.commit();

    // When world block number is 255, the trie context is calculated as:
    // ((255 + 1) / 256) * 256 = 256
    final byte[] expectedKey =
        Bytes.concatenate(location, Bytes.ofUnsignedLong(256)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  @Test
  public void blockPastCheckpointBoundaryUsesCorrectSuffix() {
    // Set world block number to 256 (at first checkpoint)
    setWorldBlockNumber(256);

    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0x445566");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(storage, tx, location, nodeHash, nodeValue);
    tx.commit();

    // When world block number is 256, the trie context is calculated as:
    // ((256 + 1) / 256) * 256 = 256
    final byte[] expectedKey =
        Bytes.concatenate(location, Bytes.ofUnsignedLong(256)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  @Test
  public void genesisAndCheckpointNodesDoNotOverwrite() {
    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes genesisNodeValue = Bytes.fromHexString("0xAABBCCDDEEFF00");
    final Bytes checkpointNodeValue = Bytes.fromHexString("0x112233445566FF");

    // Store genesis node (no world block number set)
    SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(
        storage, tx, location, nodeHash, genesisNodeValue);
    tx.commit();

    // Set world block number to trigger checkpoint suffix
    setWorldBlockNumber(255);

    // Store checkpoint node
    tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatAccountTrieNode(
        storage, tx, location, nodeHash, checkpointNodeValue);
    tx.commit();

    final byte[] genesisKey = Bytes.concatenate(location, Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final byte[] checkpointKey =
        Bytes.concatenate(location, Bytes.ofUnsignedLong(256)).toArrayUnsafe();

    final Optional<byte[]> genesisValue = storage.get(TRIE_BRANCH_STORAGE, genesisKey);
    final Optional<byte[]> checkpointValue = storage.get(TRIE_BRANCH_STORAGE, checkpointKey);

    assertThat(genesisValue).isPresent();
    assertThat(Bytes.wrap(genesisValue.get())).isEqualTo(genesisNodeValue);

    assertThat(checkpointValue).isPresent();
    assertThat(Bytes.wrap(checkpointValue.get())).isEqualTo(checkpointNodeValue);
  }

  @Test
  public void storageTrieNodeGenesisBlockUsesZeroSuffix() {
    final Hash accountHash =
        Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0xAABBCC");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatStorageTrieNode(
        storage, tx, accountHash, location, nodeHash, nodeValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(accountHash, location, Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  @Test
  public void storageTrieNodeBlockAtCheckpointBoundaryUsesCorrectSuffix() {
    setWorldBlockNumber(255);

    final Hash accountHash =
        Hash.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    final Bytes location = Bytes.fromHexString("0x0102030405060708");
    final Bytes32 nodeHash = Hash.EMPTY;
    final Bytes nodeValue = Bytes.fromHexString("0x112233");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveProofsFlatDbStrategy.putFlatStorageTrieNode(
        storage, tx, accountHash, location, nodeHash, nodeValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(accountHash, location, Bytes.ofUnsignedLong(256)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(TRIE_BRANCH_STORAGE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(nodeValue);
  }

  private void setWorldBlockNumber(final long blockNumber) {
    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    tx.put(
        TRIE_BRANCH_STORAGE,
        WORLD_BLOCK_NUMBER_KEY,
        Bytes.ofUnsignedLong(blockNumber).toArrayUnsafe());
    tx.commit();
  }
}
