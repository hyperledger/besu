/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.plugin.data;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface TrieLog {
  Hash getBlockHash();

  Optional<Long> getBlockNumber();

  void freeze();

  <U extends LogTuple<AccountValue>> Map<Address, U> getAccountChanges();

  <U extends LogTuple<Bytes>> Map<Address, U> getCodeChanges();

  <U extends LogTuple<UInt256>> Map<Address, Map<StorageSlotKey, U>> getStorageChanges();

  <U extends LogTuple<UInt256>> Map<StorageSlotKey, U> getStorageChanges(final Address address);

  Optional<Bytes> getPriorCode(final Address address);

  Optional<Bytes> getCode(final Address address);

  Optional<UInt256> getPriorStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey);

  Optional<UInt256> getStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey);

  Optional<? extends AccountValue> getPriorAccount(final Address address);

  Optional<? extends AccountValue> getAccount(final Address address);

  interface LogTuple<T> {
    T getPrior();

    T getUpdated();

    default boolean isUnchanged() {
      return Objects.equals(getUpdated(), getPrior());
    }

    boolean isCleared();
  }
}
