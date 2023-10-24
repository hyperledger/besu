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
 */
package org.hyperledger.besu.services.kvstore;

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.tuweni.bytes.Bytes;

/**
 * InMemoryKeyValueStorage is just a wrapper around a single segment instance of
 * SegmentedInMemoryKeyValueStorage.
 */
public class InMemoryKeyValueStorage extends SegmentedKeyValueStorageAdapter {

  static final SegmentIdentifier SEGMENT_IDENTIFIER =
      new SegmentIdentifier() {
        private static final String NAME = "SEGMENT_IDENTIFIER";

        @Override
        public String getName() {
          return NAME;
        }

        @Override
        public byte[] getId() {
          return NAME.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean containsStaticData() {
          return false;
        }

        @Override
        public boolean isEligibleToHighSpecFlag() {
          return false;
        }
      };

  private static ConcurrentMap<SegmentIdentifier, NavigableMap<Bytes, Optional<byte[]>>>
      asSegmentMap(final NavigableMap<Bytes, Optional<byte[]>> initialMap) {
    final ConcurrentMap<SegmentIdentifier, NavigableMap<Bytes, Optional<byte[]>>> segmentMap =
        new ConcurrentHashMap<>();
    segmentMap.put(SEGMENT_IDENTIFIER, initialMap);
    return segmentMap;
  }

  /** protected access to the rw lock. */
  protected final ReadWriteLock rwLock;

  /** Instantiates a new In memory key value storage. */
  public InMemoryKeyValueStorage() {
    this(SEGMENT_IDENTIFIER);
  }

  /**
   * Instantiates a new In memory key value storage with an initial map.
   *
   * @param initialMap the initial map
   */
  public InMemoryKeyValueStorage(final NavigableMap<Bytes, Optional<byte[]>> initialMap) {
    super(SEGMENT_IDENTIFIER, new SegmentedInMemoryKeyValueStorage(asSegmentMap(initialMap)));
    rwLock = ((SegmentedInMemoryKeyValueStorage) storage).rwLock;
  }

  /**
   * Instantiates a new In memory key value storage with a single segment identifier.
   *
   * @param segmentIdentifier the segment identifier
   */
  public InMemoryKeyValueStorage(final SegmentIdentifier segmentIdentifier) {
    super(segmentIdentifier, new SegmentedInMemoryKeyValueStorage());
    rwLock = ((SegmentedInMemoryKeyValueStorage) storage).rwLock;
  }

  /**
   * Dump the contents of the storage to the print stream.
   *
   * @param ps the print stream.
   */
  public void dump(final PrintStream ps) {
    ((SegmentedInMemoryKeyValueStorage) storage).dump(ps);
  }
}
