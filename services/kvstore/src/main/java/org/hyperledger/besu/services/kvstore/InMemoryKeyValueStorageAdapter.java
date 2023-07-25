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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.tuweni.bytes.Bytes;

/**
 * InMemoryKeyValueStorage is just a wrapper around a single segment instance of SegmentedInMemoryKeyValueStorage.
 *
 * Eventually this will replace InMemoryKeyValueStorage.
 */
public class InMemoryKeyValueStorageAdapter extends SegmentedKeyValueStorageAdapter {

  private static final SegmentIdentifier SEGMENT_IDENTIFIER = new SegmentIdentifier() {
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
  };

  private static Map<SegmentIdentifier, Map<Bytes, Optional<byte[]>>> asSegmentMap(final Map<Bytes, Optional<byte[]>> initialMap) {
    final Map<SegmentIdentifier, Map<Bytes, Optional<byte[]>>> segmentMap = new HashMap<>();
    segmentMap.put(SEGMENT_IDENTIFIER, initialMap);
    return segmentMap;
  }

  /** protected access to the rw lock. */
  protected final ReadWriteLock rwLock;

  public InMemoryKeyValueStorageAdapter() {
    super(SEGMENT_IDENTIFIER, new SegmentedInMemoryKeyValueStorage());
    rwLock = ((SegmentedInMemoryKeyValueStorage) storage).rwLock;
  }

  public InMemoryKeyValueStorageAdapter(Map<Bytes, Optional<byte[]>> initialMap) {
    super(SEGMENT_IDENTIFIER, new SegmentedInMemoryKeyValueStorage(asSegmentMap(initialMap)));
    rwLock = ((SegmentedInMemoryKeyValueStorage) storage).rwLock;
  }

  private InMemoryKeyValueStorageAdapter(final SegmentedInMemoryKeyValueStorage storage) {
    super(SEGMENT_IDENTIFIER, storage);
    rwLock = storage.rwLock;
  }
}
