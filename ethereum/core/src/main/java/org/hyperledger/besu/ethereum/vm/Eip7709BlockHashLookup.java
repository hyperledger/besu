/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.vm;

import static org.hyperledger.besu.datatypes.Hash.ZERO;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.HashMap;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves block hashes from system contract storage and caches hashes by number, used by
 * BLOCKHASH operation.
 */
public class Eip7709BlockHashLookup implements BlockHashLookup {
  private static final Logger LOG = LoggerFactory.getLogger(Eip7709BlockHashLookup.class);
  private static final long BLOCKHASH_SERVE_WINDOW = 256L;

  private final Address contractAddress;
  private final long historyServeWindow;
  private final long blockHashServeWindow;
  private final HashMap<Long, Hash> hashByNumber = new HashMap<>();

  /**
   * Constructs a Eip7709BlockHashLookup.
   *
   * @param contractAddress the address of the contract storing the history.
   * @param historyServeWindow the number of blocks for which history should be saved.
   */
  public Eip7709BlockHashLookup(final Address contractAddress, final long historyServeWindow) {
    this(contractAddress, historyServeWindow, BLOCKHASH_SERVE_WINDOW);
  }

  /**
   * Constructs a Eip7709BlockHashLookup with a specified blockHashServeWindow. This constructor is
   * only used for testing.
   *
   * @param contractAddress the address of the contract storing the history.
   * @param historyServeWindow the number of blocks for which history should be saved.
   * @param blockHashServeWindow the number of block for which contract can serve the BLOCKHASH
   *     opcode.
   */
  @VisibleForTesting
  Eip7709BlockHashLookup(
      final Address contractAddress,
      final long historyServeWindow,
      final long blockHashServeWindow) {
    this.contractAddress = contractAddress;
    this.historyServeWindow = historyServeWindow;
    this.blockHashServeWindow = blockHashServeWindow;
  }

  @Override
  public Hash apply(final MessageFrame frame, final Long blockNumber) {
    final Hash cachedHash = hashByNumber.get(blockNumber);
    if (cachedHash != null) {
      return cachedHash;
    }

    final WorldUpdater worldUpdater = frame.getWorldUpdater();
    Account account = worldUpdater.get(contractAddress);
    if (account == null) {
      LOG.error("cannot query system contract {}", contractAddress);
      return ZERO;
    }

    UInt256 slot = UInt256.valueOf(blockNumber % historyServeWindow);
    final UInt256 value = account.getStorageValue(slot);
    LOG.atTrace()
        .log(
            () ->
                String.format(
                    "Read block %s for account %s returned value %s",
                    account.getAddress(), slot.toDecimalString(), value.toString()));
    Hash blockHash = Hash.wrap(value);
    hashByNumber.put(blockNumber, blockHash);
    return blockHash;
  }

  @Override
  public long getLookback() {
    return blockHashServeWindow;
  }
}
