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
package org.hyperledger.besu.services.kvstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage.SEGMENT_IDENTIFIER;

import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class LayeredKeyValueStorageTest extends AbstractSegmentedKeyValueStorageTest {
  @Override
  protected KeyValueStorage createStore() {
    return new SegmentedKeyValueStorageAdapter(SEGMENT_IDENTIFIER, createSegmentedStore());
  }

  @Override
  public SegmentedKeyValueStorage createSegmentedStore() {
    return new LayeredKeyValueStorage(new SegmentedInMemoryKeyValueStorage());
  }

  @Test
  public void assertSegmentedIsNearestBeforeWithMultipleLayer() throws Exception {
    try (final var store = createSegmentedKeyValueStorageWithValues()) {

      final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
      final byte[] key3 = bytesFromHexString("010B");
      final byte[] value3 = bytesFromHexString("0FFF");
      tx.put(SEGMENT_IDENTIFIER, key3, value3);
      tx.commit();

      final LayeredKeyValueStorage lastKeyValueStorage = new LayeredKeyValueStorage(store);
      SegmentedKeyValueStorageTransaction segmentedKeyValueStorageTransaction =
          lastKeyValueStorage.startTransaction();

      final byte[] key1 = bytesFromHexString("01");
      final byte[] value1 = bytesFromHexString("0FFF");
      segmentedKeyValueStorageTransaction.put(SEGMENT_IDENTIFIER, key1, value1);
      final byte[] key2 = bytesFromHexString("010A");
      final byte[] value2 = bytesFromHexString("0FFF");
      segmentedKeyValueStorageTransaction.put(SEGMENT_IDENTIFIER, key2, value2);
      segmentedKeyValueStorageTransaction.commit();

      // assert 01 is closest to 0100 (01,0100*,0101)
      var val =
          lastKeyValueStorage.getNearestBefore(SEGMENT_IDENTIFIER, Bytes.fromHexString("0100"));
      assertThat(val).isPresent();
      assertThat(val.get().key()).isEqualTo(Bytes.fromHexString("01"));

      // assert parent 010B is closest to 010D (010A,010B,010D*)
      var val1 =
          lastKeyValueStorage.getNearestBefore(SEGMENT_IDENTIFIER, Bytes.fromHexString("010D"));
      assertThat(val1).isPresent();
      assertThat(val1.get().key()).isEqualTo(Bytes.fromHexString("010B"));

      // assert parent 010A is closest to 010A (
      var val2 =
          lastKeyValueStorage.getNearestBefore(SEGMENT_IDENTIFIER, Bytes.fromHexString("010A"));
      assertThat(val2).isPresent();
      assertThat(val2.get().key()).isEqualTo(Bytes.fromHexString("010A"));

      // assert parent 0001 is closest to 000121*
      var val3 =
          lastKeyValueStorage.getNearestBefore(SEGMENT_IDENTIFIER, Bytes.fromHexString("000121"));
      assertThat(val3).isPresent();
      assertThat(val3.get().key()).isEqualTo(Bytes.fromHexString("0001"));

      // assert 010b is closest to 01A1*
      var val4 =
          lastKeyValueStorage.getNearestBefore(SEGMENT_IDENTIFIER, Bytes.fromHexString("01A1"));
      assertThat(val4).isPresent();
      assertThat(val4.get().key()).isEqualTo(Bytes.fromHexString("010b"));
    }
  }

  @Test
  public void assertSegmentedIsNearestAfterWithMultipleLayer() throws Exception {
    try (final var store = createSegmentedKeyValueStorageWithValues()) {

      final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
      final byte[] key3 = bytesFromHexString("010B");
      final byte[] value3 = bytesFromHexString("0FFF");
      tx.put(SEGMENT_IDENTIFIER, key3, value3);
      tx.commit();

      final LayeredKeyValueStorage lastKeyValueStorage = new LayeredKeyValueStorage(store);
      SegmentedKeyValueStorageTransaction segmentedKeyValueStorageTransaction =
          lastKeyValueStorage.startTransaction();

      final byte[] key1 = bytesFromHexString("02");
      final byte[] value1 = bytesFromHexString("0FFF");
      segmentedKeyValueStorageTransaction.put(SEGMENT_IDENTIFIER, key1, value1);
      final byte[] key2 = bytesFromHexString("010A");
      final byte[] value2 = bytesFromHexString("0FFF");
      segmentedKeyValueStorageTransaction.put(SEGMENT_IDENTIFIER, key2, value2);
      segmentedKeyValueStorageTransaction.commit();

      // assert 02 is closest to 0100 (011D,02)
      var val =
          lastKeyValueStorage.getNearestAfter(SEGMENT_IDENTIFIER, Bytes.fromHexString("011D"));
      assertThat(val).isPresent();
      assertThat(val.get().key()).isEqualTo(Bytes.fromHexString("02"));

      // assert parent 010A is closest to 010A
      var val1 =
          lastKeyValueStorage.getNearestAfter(SEGMENT_IDENTIFIER, Bytes.fromHexString("010A"));
      assertThat(val1).isPresent();
      assertThat(val1.get().key()).isEqualTo(Bytes.fromHexString("010A"));

      // assert parent 010A is closest to 0000 (0000*, 0001, 0002) (
      var val2 =
          lastKeyValueStorage.getNearestAfter(SEGMENT_IDENTIFIER, Bytes.fromHexString("0000"));
      assertThat(val2).isPresent();
      assertThat(val2.get().key()).isEqualTo(Bytes.fromHexString("0001"));

      // assert parent 0001 is closest to 00*
      var val3 = lastKeyValueStorage.getNearestAfter(SEGMENT_IDENTIFIER, Bytes.fromHexString("00"));
      assertThat(val3).isPresent();
      assertThat(val3.get().key()).isEqualTo(Bytes.fromHexString("0001"));

      // assert 0001 is closest to 0000*
      var val4 =
          lastKeyValueStorage.getNearestAfter(SEGMENT_IDENTIFIER, Bytes.fromHexString("0000"));
      assertThat(val4).isPresent();
      assertThat(val4.get().key()).isEqualTo(Bytes.fromHexString("0001"));
    }
  }
}
