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

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;

public class SegmentedKeyValueStorageTransactionTransitionValidatorDecorator<S>
    implements Transaction<S> {

  private final Transaction<S> transaction;
  private boolean active = true;

  public SegmentedKeyValueStorageTransactionTransitionValidatorDecorator(
      final Transaction<S> toDecorate) {
    this.transaction = toDecorate;
  }

  @Override
  public final void put(final S segment, final byte[] key, final byte[] value) {
    checkState(active, "Cannot invoke put() on a completed transaction.");
    transaction.put(segment, key, value);
  }

  @Override
  public final void remove(final S segment, final byte[] key) {
    checkState(active, "Cannot invoke remove() on a completed transaction.");
    transaction.remove(segment, key);
  }

  @Override
  public final void commit() throws StorageException {
    checkState(active, "Cannot commit a completed transaction.");
    active = false;
    transaction.commit();
  }

  @Override
  public final void rollback() {
    checkState(active, "Cannot rollback a completed transaction.");
    active = false;
    transaction.rollback();
  }
}
