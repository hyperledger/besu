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
 *
 */

package org.hyperledger.besu.evm;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

/** Interface for interacting with the Storage map of a given account instance */
public interface EVMAccountState {

  /**
   * Accesses and retrieves the contract value mapped to the specified key
   *
   * @param key The key for the value.
   * @return {@link Optional} {@link UInt256} value mapped to the key if it exists; otherwise empty
   */
  Optional<UInt256> get(UInt256 key);

  /**
   * Accesses and retrieves the contract value mapped to the specified key, as it existed at the
   * beginning of the transaction
   *
   * @param key The key for the value.
   * @return {@link Optional} {@link UInt256} value mapped to the key if it exists; otherwise empty
   */
  Optional<UInt256> getOriginal(UInt256 key);

  /**
   * Updates the value mapped to the specified key, creating the mapping if one does not already
   * exist
   *
   * @param key The key that corresponds to the value to be updated.
   * @param value The value to associate the key with
   */
  void put(UInt256 key, UInt256 value);

  /**
   * Deletes the value mapped to the specified key, if such a value exists (Optional operation).
   *
   * @param key The key of the value to be deleted.
   */
  void remove(UInt256 key);

  /**
   * Clears all the storage for the account. Typically as part of contract creation or
   * self-destruct.
   */
  void clearStorage();
}
