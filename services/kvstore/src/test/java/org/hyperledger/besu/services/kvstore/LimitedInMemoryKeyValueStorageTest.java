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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import org.junit.Test;

public class LimitedInMemoryKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Override
  protected KeyValueStorage createStore() {
    return new LimitedInMemoryKeyValueStorage(100_000_000);
  }

  @Test
  public void testLimiting() {
    final long limit = 5;
    final LimitedInMemoryKeyValueStorage storage = new LimitedInMemoryKeyValueStorage(limit);

    for (int i = 0; i < limit * 2; i++) {
      final KeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(bytesOf(i), bytesOf(i));
      tx.commit();
    }

    int hits = 0;
    for (int i = 0; i < limit * 2; i++) {
      if (storage.get(bytesOf(i)).isPresent()) {
        hits++;
      }
    }

    assertThat(hits <= limit).isTrue();
    // Oldest key should've been dropped first
    assertThat(storage.containsKey(bytesOf((0)))).isFalse();
  }
}
