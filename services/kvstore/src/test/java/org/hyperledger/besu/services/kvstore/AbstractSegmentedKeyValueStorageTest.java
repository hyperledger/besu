/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.services.kvstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage.SEGMENT_IDENTIFIER;

import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public abstract class AbstractSegmentedKeyValueStorageTest extends AbstractKeyValueStorageTest {
  public abstract SegmentedKeyValueStorage createSegmentedStore();

  @Test
  public void assertSegmentedIsNearestTo() throws Exception {
    try (final var store = this.createSegmentedStore()) {

      // create 10 entries
      final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
      IntStream.range(1, 10)
          .forEach(
              i -> {
                final byte[] key = bytesFromHexString("000" + i);
                final byte[] value = bytesFromHexString("0FFF");
                tx.put(SEGMENT_IDENTIFIER, key, value);
                // different common prefix, and reversed order of bytes:
                final byte[] key2 = bytesFromHexString("010" + (10 - i));
                final byte[] value2 = bytesFromHexString("0FFF");
                tx.put(SEGMENT_IDENTIFIER, key2, value2);
              });
      tx.commit();

      // assert 0009 is closest to 000F
      var val = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("000F"));
      assertThat(val).isPresent();
      assertThat(val.get().key()).isEqualTo(Bytes.fromHexString("0009"));

      // assert 0109 is closest to 010D
      var val2 = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("010D"));
      assertThat(val2).isPresent();
      assertThat(val2.get().key()).isEqualTo(Bytes.fromHexString("0109"));

      // assert 0103 is closest to 0103
      var val3 = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0103"));
      assertThat(val3).isPresent();
      assertThat(val3.get().key()).isEqualTo(Bytes.fromHexString("0103"));

      // assert 0003 is closest to 0003
      var val4 = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0003"));
      assertThat(val4).isPresent();
      assertThat(val4.get().key()).isEqualTo(Bytes.fromHexString("0003"));

      // assert 0001 is closest to 0001
      var val5 = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0001"));
      assertThat(val5).isPresent();
      assertThat(val5.get().key()).isEqualTo(Bytes.fromHexString("0001"));

      // assert 0000 is not present
      var val6 = store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0000"));
      assertThat(val6).isNotPresent();
    }
  }

  @Test
  void txGet_returnsEmptyWhenNoValue() {
    final SegmentedKeyValueStorage store = createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    assertThat(tx.get(SEGMENT_IDENTIFIER, Bytes.fromHexString("0001").toArrayUnsafe())).isEmpty();
  }

  @Test
  void txGet_returnsUpdatedValueAfterPut() {
    final SegmentedKeyValueStorage store = createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    final byte[] key = Bytes.fromHexString("0001").toArrayUnsafe();
    final byte[] value = Bytes.fromHexString("0002").toArrayUnsafe();

    tx.put(SEGMENT_IDENTIFIER, key, value);
    assertThat(tx.get(SEGMENT_IDENTIFIER, key).map(Bytes::wrap)).contains(Bytes.wrap(value));
  }

  @Test
  void txGet_returnsEmptyAfterPutAndRemove() {
    final SegmentedKeyValueStorage store = createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    final byte[] key = Bytes.fromHexString("0001").toArrayUnsafe();
    final byte[] value = Bytes.fromHexString("0002").toArrayUnsafe();

    tx.put(SEGMENT_IDENTIFIER, key, value);
    tx.remove(SEGMENT_IDENTIFIER, key);
    assertThat(tx.get(SEGMENT_IDENTIFIER, key).map(Bytes::wrap)).isEmpty();
  }

  @Test
  void txGet_returnsUpdatedValueAfterPutRemovePut() {
    final SegmentedKeyValueStorage store = createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    final byte[] key = Bytes.fromHexString("0001").toArrayUnsafe();
    final byte[] value1 = Bytes.fromHexString("0002").toArrayUnsafe();
    final byte[] value2 = Bytes.fromHexString("0003").toArrayUnsafe();

    tx.put(SEGMENT_IDENTIFIER, key, value1);
    tx.remove(SEGMENT_IDENTIFIER, key);
    tx.put(SEGMENT_IDENTIFIER, key, value2);
    assertThat(tx.get(SEGMENT_IDENTIFIER, key).map(Bytes::wrap)).contains(Bytes.wrap(value2));
  }

  @Test
  void txGet_returnsUpdatedValueAfterPutPut() {
    final SegmentedKeyValueStorage store = createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    final byte[] key = Bytes.fromHexString("0001").toArrayUnsafe();
    final byte[] value1 = Bytes.fromHexString("0002").toArrayUnsafe();
    final byte[] value2 = Bytes.fromHexString("0003").toArrayUnsafe();

    tx.put(SEGMENT_IDENTIFIER, key, value1);
    tx.put(SEGMENT_IDENTIFIER, key, value2);
    assertThat(tx.get(SEGMENT_IDENTIFIER, key).map(Bytes::wrap)).contains(Bytes.wrap(value2));
  }
}
