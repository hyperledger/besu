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

package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class BothCodeStorageStrategy implements CodeStorageStrategy {

  private final DefaultCodeStorageStrategy defaultCodeStorageStrategy;
  private final LegacyCodeStorageStrategy legacyCodeStorageStrategy;

  public BothCodeStorageStrategy() {
    defaultCodeStorageStrategy = new DefaultCodeStorageStrategy();
    legacyCodeStorageStrategy = new LegacyCodeStorageStrategy();
  }

  @Override
  public Optional<Bytes> getFlatCode(
      Hash codeHash, Hash accountHash, SegmentedKeyValueStorage storage) {
    return defaultCodeStorageStrategy.getFlatCode(codeHash, accountHash, storage);
  }

  @Override
  public void putFlatCode(
      SegmentedKeyValueStorageTransaction transaction,
      Hash accountHash,
      Hash codeHash,
      Bytes code) {
    defaultCodeStorageStrategy.putFlatCode(transaction, accountHash, codeHash, code);
    legacyCodeStorageStrategy.putFlatCode(transaction, accountHash, codeHash, code);
  }

  @Override
  public void removeFlatCode(
      SegmentedKeyValueStorageTransaction transaction, Hash accountHash, Hash codeHash) {
    defaultCodeStorageStrategy.removeFlatCode(transaction, accountHash, codeHash);
    legacyCodeStorageStrategy.removeFlatCode(transaction, accountHash, codeHash);
  }

  @Override
  public void clear(SegmentedKeyValueStorage storage) {
    defaultCodeStorageStrategy.clear(storage);
    legacyCodeStorageStrategy.clear(storage);
  }
}
