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
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.function.Supplier;

/**
 * The type Segmented key value storage adapter.
 *
 * @param <S> the type parameter
 */
public class SnappableSegmentedKeyValueStorageAdapter<S> extends SegmentedKeyValueStorageAdapter<S>
    implements SnappableKeyValueStorage {
  private final Supplier<SnappedKeyValueStorage> snapshotSupplier;

  /**
   * Instantiates a new Segmented key value storage adapter.
   *
   * @param segment the segment
   * @param storage the storage
   */
  public SnappableSegmentedKeyValueStorageAdapter(
      final SegmentIdentifier segment, final SegmentedKeyValueStorage<S> storage) {
    this(
        segment,
        storage,
        () -> {
          throw new UnsupportedOperationException("Snapshot not supported");
        });
  }

  /**
   * Instantiates a new Segmented key value storage adapter.
   *
   * @param segment the segment
   * @param storage the storage
   * @param snapshotSupplier the snapshot supplier
   */
  public SnappableSegmentedKeyValueStorageAdapter(
      final SegmentIdentifier segment,
      final SegmentedKeyValueStorage<S> storage,
      final Supplier<SnappedKeyValueStorage> snapshotSupplier) {
    super(segment, storage);
    this.snapshotSupplier = snapshotSupplier;
  }

  @Override
  public SnappedKeyValueStorage takeSnapshot() {
    return snapshotSupplier.get();
  }
}
