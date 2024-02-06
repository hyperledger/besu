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
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.units.bigints.UInt256;

/** A helper class to store the historical block hash. */
public class HistoricalBlockHashProcessor {

  private static final long HISTORY_SERVE_WINDOW = 256;
  public static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  public HistoricalBlockHashProcessor() {}

  public void storeHistoricalBlockHashes(
      final Blockchain blockchain,
      final WorldUpdater worldUpdater,
      final BlockHeader currentBlockHeader) {
    /*
     see EIP-2935: https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2935.md
    */
    final MutableAccount account = worldUpdater.getOrCreate(HISTORY_STORAGE_ADDRESS);

    if (currentBlockHeader.getNumber() > 0) {
      final long currentBlockNumber = currentBlockHeader.getNumber();
      final UInt256 storageValue = account.getStorageValue(UInt256.valueOf(currentBlockNumber - 1));
      if (storageValue.isZero()) { // this is the first block of the fork
        final long rangeSize = Math.min(HISTORY_SERVE_WINDOW - 1, currentBlockNumber - 1);
        for (long i = 0; i <= rangeSize; i++) {
          final BlockHeader blockHeader =
              blockchain.getBlockHeader(currentBlockNumber - 1 - i).orElseThrow();
          account.setStorageValue(
              UInt256.valueOf(blockHeader.getNumber()),
              UInt256.fromBytes(blockHeader.getBlockHash()));
        }
      } else {
        account.setStorageValue(
            UInt256.valueOf(currentBlockNumber),
            UInt256.fromBytes(currentBlockHeader.getBlockHash()));
      }
    }
  }
}
