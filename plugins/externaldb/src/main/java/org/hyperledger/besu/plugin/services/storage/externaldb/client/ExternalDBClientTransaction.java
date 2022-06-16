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
package org.hyperledger.besu.plugin.services.storage.externaldb.client;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;

public class ExternalDBClientTransaction implements Transaction<String> {

  @Override
  public void put(final String segment, final byte[] key, final byte[] value) {}

  @Override
  public void remove(final String segment, final byte[] key) {}

  @Override
  public void commit() throws StorageException {}

  @Override
  public void rollback() {}
}
