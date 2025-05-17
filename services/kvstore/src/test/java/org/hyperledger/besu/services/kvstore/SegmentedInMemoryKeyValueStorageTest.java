/*
 * Copyright contributors to Besu.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SegmentedInMemoryKeyValueStorageTest {

  private SegmentedInMemoryKeyValueStorage storage;
  private SegmentIdentifier segmentA;
  private SegmentIdentifier segmentB;

  @BeforeEach
  void setUp() {
    storage = new SegmentedInMemoryKeyValueStorage();
    segmentA = mock(SegmentIdentifier.class);
    segmentB = mock(SegmentIdentifier.class);
  }

  @Test
  void multigetReturnsValuesForExistingKeys() throws StorageException {
    byte[] keyA = new byte[] {1};
    byte[] valueA = new byte[] {10};
    byte[] keyB = new byte[] {2};
    byte[] valueB = new byte[] {20};

    SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
    tx.put(segmentA, keyA, valueA);
    tx.put(segmentB, keyB, valueB);
    tx.commit();

    List<SegmentIdentifier> segments = List.of(segmentA, segmentB);
    List<byte[]> keys = List.of(keyA, keyB);
    List<byte[]> results = storage.multiget(segments, keys);

    assertEquals(2, results.size(), "Expected two elements in the result list");
    assertArrayEquals(valueA, results.get(0), "Value for keyA is incorrect");
    assertArrayEquals(valueB, results.get(1), "Value for keyB is incorrect");
  }

  @Test
  void multigetReturnsNullForMissingKey() throws StorageException {
    byte[] keyA = new byte[] {1};

    List<SegmentIdentifier> segments = List.of(segmentA);
    List<byte[]> keys = List.of(keyA);
    List<byte[]> results = storage.multiget(segments, keys);

    assertEquals(1, results.size(), "Expected one element in the result list");
    assertNull(results.get(0), "Expected null for missing key");
  }

  @Test
  void multigetThrowsExceptionWhenSegmentsAndKeysSizeMismatch() {
    byte[] keyA = new byte[] {1};
    byte[] keyB = new byte[] {2};

    List<SegmentIdentifier> segments = List.of(segmentA);
    List<byte[]> keys = List.of(keyA, keyB);

    assertThrows(
        IllegalArgumentException.class,
        () -> storage.multiget(segments, keys),
        "Expected IllegalArgumentException when segments and keys sizes differ");
  }
}
