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
 */
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.operations.BlockHashOperation;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates and caches block hashes by number following the chain for a specific branch. This is
 * used by {@link BlockHashOperation} and ensures that the correct block hash is returned even when
 * the block being imported is on a fork.
 *
 * <p>A new BlockHashCache must be created for each block being processed but should be reused for
 * all transactions within that block.
 */
public class BlockHashLookup {

  private ProcessableBlockHeader searchStartHeader;
  private final Blockchain blockchain;
  private final Map<Long, Hash> hashByNumber = new HashMap<>();

  public BlockHashLookup(final ProcessableBlockHeader currentBlock, final Blockchain blockchain) {
    this.searchStartHeader = currentBlock;
    this.blockchain = blockchain;
    hashByNumber.put(currentBlock.getNumber() - 1, currentBlock.getParentHash());
  }

  public Hash getBlockHash(final long blockNumber) {
    final Hash cachedHash = hashByNumber.get(blockNumber);
    if (cachedHash != null) {
      return cachedHash;
    }
    while (searchStartHeader != null && searchStartHeader.getNumber() - 1 > blockNumber) {
      searchStartHeader = blockchain.getBlockHeader(searchStartHeader.getParentHash()).orElse(null);
      if (searchStartHeader != null) {
        hashByNumber.put(searchStartHeader.getNumber() - 1, searchStartHeader.getParentHash());
      }
    }
    return hashByNumber.getOrDefault(blockNumber, Hash.ZERO);
  }
}
