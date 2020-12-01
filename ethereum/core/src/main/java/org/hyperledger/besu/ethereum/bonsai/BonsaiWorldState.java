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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.WorldView;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public interface BonsaiWorldState extends WorldView {

  Bytes getCode(Address address);

  UInt256 getStorageValue(Address address, UInt256 key);

  Optional<UInt256> getStorageValueBySlotHash(Address address, Hash slotHash);

  UInt256 getOriginalStorageValue(Address address, UInt256 key);

  /**
   * Stream all the storage values of a account.
   *
   * @param address the account to stream
   * @param rootHash the root hash of the account storage trie
   * @return A map that is a copy of the entries. The key is the hashed slot number, and the value
   *     is the Bytes representation of the storage value.
   */
  Map<Bytes32, Bytes> getAllAccountStorage(Address address, Hash rootHash);
}
