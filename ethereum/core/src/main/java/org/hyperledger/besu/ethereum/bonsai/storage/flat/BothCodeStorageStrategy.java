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

  private final CodeHashCodeStorageStrategy codeHashCodeStorageStrategy;
  private final AccountHashCodeStorageStrategy accountHashCodeStorageStrategy;

  public BothCodeStorageStrategy() {
    codeHashCodeStorageStrategy = new CodeHashCodeStorageStrategy();
    accountHashCodeStorageStrategy = new AccountHashCodeStorageStrategy();
  }

  @Override
  public Optional<Bytes> getFlatCode(
      final Hash codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    return codeHashCodeStorageStrategy.getFlatCode(codeHash, accountHash, storage);
  }

  @Override
  public void putFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash,
      final Bytes code) {
    codeHashCodeStorageStrategy.putFlatCode(transaction, accountHash, codeHash, code);
    accountHashCodeStorageStrategy.putFlatCode(transaction, accountHash, codeHash, code);
  }

  @Override
  public void removeFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash) {
    codeHashCodeStorageStrategy.removeFlatCode(transaction, accountHash, codeHash);
    accountHashCodeStorageStrategy.removeFlatCode(transaction, accountHash, codeHash);
  }

  @Override
  public void clear(final SegmentedKeyValueStorage storage) {
    codeHashCodeStorageStrategy.clear(storage);
    accountHashCodeStorageStrategy.clear(storage);
  }
}
