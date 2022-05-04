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
 */
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;

public class RocksDbSegmentIdentifier {

  private final TransactionDB db;
  private final AtomicReference<ColumnFamilyHandle> reference;

  public RocksDbSegmentIdentifier(
      final TransactionDB db, final ColumnFamilyHandle columnFamilyHandle) {
    this.db = db;
    this.reference = new AtomicReference<>(columnFamilyHandle);
  }

  public void reset() {
    reference.getAndUpdate(
        oldHandle -> {
          try {
            ColumnFamilyDescriptor descriptor =
                new ColumnFamilyDescriptor(
                    oldHandle.getName(), oldHandle.getDescriptor().getOptions());
            db.dropColumnFamily(oldHandle);
            ColumnFamilyHandle newHandle = db.createColumnFamily(descriptor);
            oldHandle.close();
            return newHandle;
          } catch (final RocksDBException e) {
            throw new StorageException(e);
          }
        });
  }

  public ColumnFamilyHandle get() {
    return reference.get();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RocksDbSegmentIdentifier that = (RocksDbSegmentIdentifier) o;
    return Objects.equals(reference.get(), that.reference.get());
  }

  @Override
  public int hashCode() {
    return reference.get().hashCode();
  }
}
