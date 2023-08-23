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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A helper class to store the parent beacon block root. */
public class ParentBeaconBlockRootHelper {

  // Modulus use to for the timestamp to store the  root
  public static final long HISTORICAL_ROOTS_MODULUS = 98304;

  // Address of the system user, that is used to call the contract for storing the root
  // public static final Address SYSTEM_ADDRESS =
  // Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  // The address of the contract that stores the roots
  // public static final Address BEACON_ROOTS_ADDRESS =
  // Address.fromHexString("0x89e64Be8700cC37EB34f9209c96466DEEDc0d8a6");

  public static void storeParentBeaconBlockRoot(
      final WorldUpdater worldUpdater, final long timestamp, final Bytes32 root) {
    /*
     pseudo code from EIP 4788:
     timestamp_as_uint256 = to_uint256_be(block_header.timestamp)
     parent_beacon_block_root = block_header.parent_beacon_block_root

     sstore(HISTORY_STORAGE_ADDRESS, timestamp_index, timestamp_as_uint256)
     sstore(HISTORY_STORAGE_ADDRESS, root_index, parent_beacon_block_root)
    */
    final long timestampReduced = timestamp % HISTORICAL_ROOTS_MODULUS;
    final long timestampExtended = timestampReduced + HISTORICAL_ROOTS_MODULUS;

    final UInt256 timestampIndex = UInt256.valueOf(timestampReduced);
    final UInt256 rootIndex = UInt256.valueOf(timestampExtended);

    final MutableAccount account =
        worldUpdater.getOrCreate(Address.PARENT_BEACON_BLOCK_ROOT_REGISTRY).getMutable();
    account.setStorageValue(timestampIndex, UInt256.valueOf(timestamp));
    account.setStorageValue(rootIndex, UInt256.fromBytes(root));
    worldUpdater.commit();
  }
}
