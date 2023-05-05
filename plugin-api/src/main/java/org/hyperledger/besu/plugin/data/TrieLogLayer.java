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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateTrieAccountValue;
import org.hyperledger.besu.datatypes.StorageSlotKey;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface TrieLogLayer<T extends TrieLogLayer.LogTuple<?>> {
  Hash getBlockHash();

  Optional<Long> getBlockNumber();

  <U extends LogTuple<StateTrieAccountValue>> Stream<Map.Entry<Address, U>> streamAccountChanges();

  <U extends LogTuple<Bytes>> Stream<Map.Entry<Address, U>> streamCodeChanges();

  <U extends LogTuple<UInt256>>
      Stream<Map.Entry<Address, Map<StorageSlotKey, U>>> streamStorageChanges();

  <U extends LogTuple<UInt256>> Stream<Map.Entry<StorageSlotKey, U>> streamStorageChanges(
      final Address address);

  Optional<Bytes> getPriorCode(final Address address);

  Optional<Bytes> getCode(final Address address);

  Optional<UInt256> getPriorStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey);

  Optional<UInt256> getStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey);

  Optional<? extends StateTrieAccountValue> getPriorAccount(final Address address);

  Optional<? extends StateTrieAccountValue> getAccount(final Address address);

  interface LogTuple<T> {
    T getPrior();

    T getUpdated();
  }
}
