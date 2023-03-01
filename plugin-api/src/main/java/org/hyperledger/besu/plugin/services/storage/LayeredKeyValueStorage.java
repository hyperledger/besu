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
package org.hyperledger.besu.plugin.services.storage;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LayeredKeyValueStorage extends InMemoryKeyValueStorage implements SnappedKeyValueStorage{

  private final SnappedKeyValueStorage parent;

  public LayeredKeyValueStorage(final SnappedKeyValueStorage parent) {
    super();
    this.parent = parent;
  }

  public LayeredKeyValueStorage(final SnappedKeyValueStorage parent, final Map<Bytes, byte[]> hashValueStore) {
    super(hashValueStore);
    this.parent = parent;
  }


  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return get(key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      final Bytes keyWrapped = Bytes.wrap(key);
      byte[] res = hashValueStore.get(keyWrapped);
      if(res.length==0) {
        return Optional.empty();
      }
      return Optional.ofNullable(hashValueStore.get(keyWrapped)).or(() -> parent.get(key));
    } finally {
      lock.unlock();
    }
  }



  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    // TODO
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    // TODO
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream() {
    // TODO
  }

  @Override
  public Stream<byte[]> streamKeys() {
    // TODO
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    // TODO
  }

  @Override
    public KeyValueStorageTransaction startTransaction() {
      return new KeyValueStorageTransactionTransitionValidatorDecorator(new InMemoryTransaction(){
        @Override
        public void commit() throws StorageException {
          final Lock lock = rwLock.writeLock();
          lock.lock();
          try {
            hashValueStore.putAll(updatedValues);
            removedKeys.forEach(key -> hashValueStore.put(key , new byte[]{}));
            // put empty and not removed to not ask parent in case of deletion
            updatedValues = null;
            removedKeys = null;
          } finally {
            lock.unlock();
          }
        }
      });
    }
}
