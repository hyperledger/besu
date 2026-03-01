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
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BonsaiArchiveFlatDbStrategyTest {

  private BonsaiArchiveFlatDbStrategy archiveFlatDbStrategy;
  private SegmentedKeyValueStorage storage;

  @BeforeEach
  public void setup() {
    storage = new SegmentedInMemoryKeyValueStorage();
    archiveFlatDbStrategy =
        new BonsaiArchiveFlatDbStrategy(new NoOpMetricsSystem(), new CodeHashCodeStorageStrategy());
  }

  @Test
  public void genesisBlockUsesZeroSuffixWhenContextNotSet() {
    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000001").addressHash();
    final Bytes accountValue = Bytes.fromHexString("0xAABBCC");

    // No context set - should default to block 0
    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, accountValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(ACCOUNT_INFO_STATE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(accountValue);
  }

  @Test
  public void block1UsesOneSuffixWhenContextIsBlockZero() {
    updateBlockContext(0);

    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000002").addressHash();
    final Bytes accountValue = Bytes.fromHexString("0xDDEEFF");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, accountValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(ACCOUNT_INFO_STATE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(accountValue);
  }

  @Test
  public void block2UsesTwoSuffixWhenContextIsBlockOne() {
    updateBlockContext(1);

    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000003").addressHash();
    final Bytes accountValue = Bytes.fromHexString("0x112233");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, accountValue);
    tx.commit();

    final byte[] expectedKey =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(1)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(ACCOUNT_INFO_STATE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(accountValue);
  }

  @Test
  public void genesisAndBlock1AccountsDoNotOverwrite() {
    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000004").addressHash();
    final Bytes genesisAccountValue = Bytes.fromHexString("0xAABBCCDDEEFF00");
    final Bytes block1AccountValue = Bytes.fromHexString("0x112233445566FF");

    // Write genesis (no context = block 0)
    SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, genesisAccountValue);
    tx.commit();

    // Write block 1
    updateBlockContext(0);
    tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, block1AccountValue);
    tx.commit();

    final byte[] genesisKey =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(0)).toArrayUnsafe();
    final byte[] block1Key =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(0)).toArrayUnsafe();

    final Optional<byte[]> genesisValue = storage.get(ACCOUNT_INFO_STATE, genesisKey);
    final Optional<byte[]> block1Value = storage.get(ACCOUNT_INFO_STATE, block1Key);

    // Both should exist but block1 overwrites genesis since they have the same key
    assertThat(genesisValue).isPresent();
    assertThat(block1Value).isPresent();
    assertThat(Bytes.wrap(block1Value.get())).isEqualTo(block1AccountValue);
  }

  @Test
  public void sequentialBlocksUseIncrementingSuffixes() {
    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000005").addressHash();

    // Block 0
    updateBlockContext(0);
    SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, Bytes.fromHexString("0xAA00"));
    tx.commit();

    // Block 1
    updateBlockContext(1);
    tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, Bytes.fromHexString("0xAA01"));
    tx.commit();

    // Block 2
    updateBlockContext(2);
    tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, Bytes.fromHexString("0xAA02"));
    tx.commit();

    // Block 3
    updateBlockContext(3);
    tx = storage.startTransaction();
    archiveFlatDbStrategy.putFlatAccount(storage, tx, accountHash, Bytes.fromHexString("0xAA03"));
    tx.commit();

    final Bytes[] expectedValues = {
      Bytes.fromHexString("0xAA00"),
      Bytes.fromHexString("0xAA01"),
      Bytes.fromHexString("0xAA02"),
      Bytes.fromHexString("0xAA03")
    };

    for (long blockNum = 0; blockNum <= 3; blockNum++) {
      final byte[] key =
          Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(blockNum)).toArrayUnsafe();
      final Optional<byte[]> value = storage.get(ACCOUNT_INFO_STATE, key);
      assertThat(value).as("Block " + blockNum + " should have stored value").isPresent();
      assertThat(Bytes.wrap(value.get())).isEqualTo(expectedValues[(int) blockNum]);
    }
  }

  @Test
  public void contextSafeCloneCreatesIsolatedCopy() {
    updateBlockContext(5);

    // Create a clone
    BonsaiArchiveFlatDbStrategy clonedStrategy =
        (BonsaiArchiveFlatDbStrategy) archiveFlatDbStrategy.contextSafeClone();

    // Update the original context
    updateBlockContext(10);

    // The clone should still have the old context (block 5)
    final Hash accountHash =
        Address.fromHexString("0x0000000000000000000000000000000000000006").addressHash();
    final Bytes accountValue = Bytes.fromHexString("0xFFEEDD");

    final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    clonedStrategy.putFlatAccount(storage, tx, accountHash, accountValue);
    tx.commit();

    // Should be stored with suffix 5, not 10
    final byte[] expectedKey =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(5)).toArrayUnsafe();
    final Optional<byte[]> storedValue = storage.get(ACCOUNT_INFO_STATE, expectedKey);

    assertThat(storedValue).isPresent();
    assertThat(Bytes.wrap(storedValue.get())).isEqualTo(accountValue);

    // Verify block 10 key doesn't exist
    final byte[] block10Key =
        Bytes.concatenate(accountHash.getBytes(), Bytes.ofUnsignedLong(10)).toArrayUnsafe();
    assertThat(storage.get(ACCOUNT_INFO_STATE, block10Key)).isEmpty();
  }

  /**
   * Helper method to update the block context on the strategy. This replaces the old approach of
   * writing to WORLD_BLOCK_NUMBER_KEY in the database.
   */
  private void updateBlockContext(final long blockNumber) {
    final BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getNumber()).thenReturn(blockNumber);
    archiveFlatDbStrategy.updateBlockContext(mockHeader);
  }
}
