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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

/** The KZGPointEval precompile contract. */
public class ParentBeaconBlockRootPrecompiledContract implements PrecompiledContract {

  public static final long HISTORICAL_ROOTS_MODULUS = 98304;
  public static final int G_BEACON_ROOT = 4200;

  @Override
  public String getName() {
    return "ParentBeaconBlockRoot";
  }

  @Override
  public long gasRequirement(final Bytes input) {
    // As defined in EIP-4788
    return G_BEACON_ROOT;
  }

  @NotNull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes timestampBytes, @NotNull final MessageFrame messageFrame) {
    if (timestampBytes.size() != 32) {
      return PrecompileContractResult.revert(Bytes.EMPTY); // EIP 4788 pseudo code: evm.revert()
    }
    final long timestampLong = timestampBytes.getLong(0);
    final long timestampReduced = timestampLong % HISTORICAL_ROOTS_MODULUS;
    final UInt256 index = UInt256.valueOf(timestampReduced);

    final WorldUpdater worldUpdater = messageFrame.getWorldUpdater();
    final EvmAccount account = worldUpdater.getAccount(Address.PARENT_BEACON_BLOCK_ROOT_REGISTRY);
    final UInt256 recordedTimestamp = account.getStorageValue(index);

    if (!recordedTimestamp.equals(timestampBytes)) {
      return PrecompileContractResult.success(Bytes32.ZERO);
    }

    index.add(HISTORICAL_ROOTS_MODULUS);
    final UInt256 root = account.getStorageValue(index);

    return PrecompileContractResult.success(root);
  }

  public static void storeParentBeaconBlockRoot(
      final WorldUpdater worldUpdater, final long timestamp, final Bytes32 root) {
    final long timestampReduced = timestamp % HISTORICAL_ROOTS_MODULUS;
    final long timestampExtended = timestampReduced + HISTORICAL_ROOTS_MODULUS;

    final UInt256 timestampIndex = UInt256.valueOf(timestampReduced);
    final UInt256 rootIndex = UInt256.valueOf(timestampExtended);

    final MutableAccount account =
        worldUpdater.getOrCreate(Address.PARENT_BEACON_BLOCK_ROOT_REGISTRY).getMutable();
    account.setStorageValue(timestampIndex, UInt256.valueOf(timestamp));
    account.setStorageValue(rootIndex, UInt256.fromBytes(root));
    // TODO: Is UInt256 big endian?
    /*
     from EIP 4788:
     timestamp_as_uint256 = to_uint256_be(block_header.timestamp)
     parent_beacon_block_root = block_header.parent_beacon_block_root

     sstore(HISTORY_STORAGE_ADDRESS, timestamp_index, timestamp_as_uint256)
     sstore(HISTORY_STORAGE_ADDRESS, root_index, parent_beacon_block_root)
    */
    worldUpdater.commit();
  }
}
