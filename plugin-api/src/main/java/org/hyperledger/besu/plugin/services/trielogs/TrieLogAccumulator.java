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
package org.hyperledger.besu.plugin.services.trielogs;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.plugin.data.TrieLog;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface TrieLogAccumulator {

  <T extends AccountValue, U extends TrieLog.LogTuple<T>>
      Map<Address, U> getAccountsToUpdate();

  <U extends TrieLog.LogTuple<Bytes>> Map<Address, U> getCodeToUpdate();

  <U extends TrieLog.LogTuple<UInt256>>
      Map<Address, ? extends Map<StorageSlotKey, U>> getStorageToUpdate();
}
