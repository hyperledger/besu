/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A helper class to store the parent beacon block root. */
public interface ParentBeaconBlockRootHelper {

  // Modulus to use for the timestamp to store the root
  long HISTORY_BUFFER_LENGTH = 8191;
  Address BEACON_ROOTS_ADDRESS =
      Address.fromHexString("0x000F3df6D732807Ef1319fB7B8bB8522d0Beac02");

  static void storeParentBeaconBlockRoot(
      final WorldUpdater worldUpdater, final long timestamp, final Bytes32 root) {
    /*
     see EIP-4788: https://github.com/ethereum/EIPs/blob/master/EIPS/eip-4788.md
    */
    // If code is not deployed don't do anything
    final MutableAccount account = worldUpdater.getAccount(BEACON_ROOTS_ADDRESS);
    if (account == null || Hash.EMPTY.equals(account.getCodeHash())) {
      return;
    }

    final long timestampReduced = Long.remainderUnsigned(timestamp, HISTORY_BUFFER_LENGTH);
    final long timestampExtended = timestampReduced + HISTORY_BUFFER_LENGTH;

    final UInt256 timestampIndex = UInt256.valueOf(timestampReduced);
    final UInt256 rootIndex = UInt256.valueOf(timestampExtended);

    account.setStorageValue(timestampIndex, UInt256.fromBytes(Bytes.ofUnsignedLong(timestamp)));
    account.setStorageValue(rootIndex, UInt256.fromBytes(root));
    worldUpdater.commit();
  }
}
