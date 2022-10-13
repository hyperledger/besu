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
package org.hyperledger.besu.ethereum.bonsai.light;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class EmptyKeyValueStorage implements KeyValueStorage {

  @Override
  public void clear() throws StorageException {}

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return Optional.empty();
  }

  @Override
  public Stream<byte[]> streamKeys() throws StorageException {
    return Stream.empty();
  }

  @Override
  public boolean tryDelete(final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return Collections.emptySet();
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    return new EmptyKeyValueStorageTransaction();
  }

  @Override
  public void close() throws IOException {}
}
