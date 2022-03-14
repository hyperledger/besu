/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.Optional;

public class GenericKeyValueStorage<K, V> {
  protected final KeyValueStorage storage;
  private final KeyConvertor<K> keyConvertor;
  private final ValueConvertor<V> valueConvertor;

  public GenericKeyValueStorage(
      final StorageProvider provider,
      final SegmentIdentifier segment,
      final KeyConvertor<K> keyConvertor,
      final ValueConvertor<V> valueConvertor) {
    this.keyConvertor = keyConvertor;
    this.valueConvertor = valueConvertor;
    this.storage = provider.getStorageBySegmentIdentifier(segment);
  }

  public Optional<V> get(final K key) {
    return storage.get(keyConvertor.toBytes(key)).map(valueConvertor::fromBytes);
  }

  public void put(final K key, final V value) {
    final KeyValueStorageTransaction keyValueStorageTransaction = storage.startTransaction();
    keyValueStorageTransaction.put(keyConvertor.toBytes(key), valueConvertor.toBytes(value));
    keyValueStorageTransaction.commit();
  }

  public void drop(final K key) {
    storage.tryDelete(keyConvertor.toBytes(key));
  }
}
