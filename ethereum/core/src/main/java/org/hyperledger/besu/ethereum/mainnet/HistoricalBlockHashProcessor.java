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
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;

/** A helper class to store the historical block hash (eip-2935) */
public class HistoricalBlockHashProcessor {

  public static final Address HISTORICAL_BLOCKHASH_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  private static final long HISTORY_SAVE_WINDOW = 8192;

  private final long forkTimestamp;

  public HistoricalBlockHashProcessor(final long forkTimestamp) {
    this.forkTimestamp = forkTimestamp;
  }

  public void storeHistoricalBlockHashes(
      final Blockchain blockchain,
      final WorldUpdater worldUpdater,
      final ProcessableBlockHeader currentBlockHeader) {

    final MutableAccount account = worldUpdater.getOrCreate(HISTORICAL_BLOCKHASH_ADDRESS);

    // If this is not the genesis block
    if (currentBlockHeader.getNumber() > 0) {
      account.setStorageValue(
          UInt256.valueOf((currentBlockHeader.getNumber() - 1) % HISTORY_SAVE_WINDOW),
          UInt256.fromBytes(currentBlockHeader.getParentHash()));

      BlockHeader ancestor =
          blockchain.getBlockHeader(currentBlockHeader.getParentHash()).orElseThrow();
      // If this is the first fork block, add the parent's direct HISTORY_SAVE_WINDOW ancestors as
      // well
      if (ancestor.getTimestamp() < forkTimestamp) {
        for (int i = 0; i < HISTORY_SAVE_WINDOW && ancestor.getNumber() > 0; i++) {
          ancestor = blockchain.getBlockHeader(ancestor.getParentHash()).orElseThrow();
          account.setStorageValue(
              UInt256.valueOf(ancestor.getNumber() % HISTORY_SAVE_WINDOW),
              UInt256.fromBytes(ancestor.getHash()));
        }
      }
    }
  }
}
