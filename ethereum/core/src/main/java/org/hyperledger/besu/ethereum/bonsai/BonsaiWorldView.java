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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public interface BonsaiWorldView extends WorldView {

  Optional<Bytes> getCode(Address address);

  Optional<Bytes> getStateTrieNode(Bytes location);

  UInt256 getStorageValue(Address address, UInt256 key);

  Optional<UInt256> getStorageValueBySlotHash(Address address, Hash slotHash);

  UInt256 getPriorStorageValue(Address address, UInt256 key);

  /**
   * Retrieve all the storage values of a account.
   *
   * @param address the account to stream
   * @param rootHash the root hash of the account storage trie
   * @return A map that is a copy of the entries. The key is the hashed slot number, and the value
   *     is the Bytes representation of the storage value.
   */
  Map<Bytes32, Bytes> getAllAccountStorage(Address address, Hash rootHash);

  static Bytes encodeTrieValue(final Bytes bytes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytes(bytes.trimLeadingZeros());
    return out.encoded();
  }
}
