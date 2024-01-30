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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractSegmentedKeyValueStorageTest extends AbstractKeyValueStorageTest {
  public abstract SegmentedKeyValueStorage createSegmentedStore();

  private static final byte[] KEY = Bytes.fromHexString("0001").toArrayUnsafe();
  private static final byte[] INITIAL_VALUE = Bytes.fromHexString("0000").toArrayUnsafe();
  private static final byte[] VALUE1 = Bytes.fromHexString("0002").toArrayUnsafe();
  private static final byte[] VALUE2 = Bytes.fromHexString("0003").toArrayUnsafe();

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
  void txGet_returnsValueForExistingValue() {
    final SegmentedKeyValueStorage store = createPopulatedSegmentedStore();
    final var tx = store.startTransaction();
    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY).map(Bytes::wrap))
        .contains(Bytes.wrap(INITIAL_VALUE));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void txGet_returnsEmptyAfterRemove(final boolean populatedDb) {
    final SegmentedKeyValueStorage store =
        populatedDb ? createPopulatedSegmentedStore() : createSegmentedStore();

    final var tx = store.startTransaction();
    tx.remove(SEGMENT_IDENTIFIER, KEY);

    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void txGet_returnsUpdatedValueAfterPut(final boolean populatedDb) {
    final SegmentedKeyValueStorage store =
        populatedDb ? createPopulatedSegmentedStore() : createSegmentedStore();
    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE1);
    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY).map(Bytes::wrap)).contains(Bytes.wrap(VALUE1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void txGet_returnsEmptyAfterPutAndRemove(final boolean populatedDb) {
    final SegmentedKeyValueStorage store =
        populatedDb ? createPopulatedSegmentedStore() : createSegmentedStore();

    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE1);
    tx.remove(SEGMENT_IDENTIFIER, KEY);

    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY).map(Bytes::wrap)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void txGet_returnsUpdatedValueAfterPutRemovePut(final boolean populatedDb) {
    final SegmentedKeyValueStorage store =
        populatedDb ? createPopulatedSegmentedStore() : createSegmentedStore();

    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE1);
    tx.remove(SEGMENT_IDENTIFIER, KEY);
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE2);

    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY).map(Bytes::wrap)).contains(Bytes.wrap(VALUE2));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void txGet_returnsUpdatedValueAfterPutPut(final boolean populatedDb) {
    final SegmentedKeyValueStorage store =
        populatedDb ? createPopulatedSegmentedStore() : createSegmentedStore();

    final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE1);
    tx.put(SEGMENT_IDENTIFIER, KEY, VALUE2);

    assertThat(tx.get(SEGMENT_IDENTIFIER, KEY).map(Bytes::wrap)).contains(Bytes.wrap(VALUE2));
  }

  private SegmentedKeyValueStorage createPopulatedSegmentedStore() {
    final SegmentedKeyValueStorage segmentedStore = createSegmentedStore();
    SegmentedKeyValueStorageTransaction tx = segmentedStore.startTransaction();
    tx.put(SEGMENT_IDENTIFIER, KEY, Bytes.fromHexString("0000").toArrayUnsafe());
    tx.commit();
    return segmentedStore;
  }
}
