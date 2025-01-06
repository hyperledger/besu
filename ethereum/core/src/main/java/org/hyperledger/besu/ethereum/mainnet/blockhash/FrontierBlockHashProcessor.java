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
package org.hyperledger.besu.ethereum.mainnet.blockhash;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.BlockchainBasedBlockHashLookup;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

public class FrontierBlockHashProcessor implements BlockHashProcessor {

  @Override
  public void processBlockHashes(
      final MutableWorldState mutableWorldState, final ProcessableBlockHeader currentBlockHeader) {
    // do nothing
  }

  /**
   * Creates a new BlockHashLookup function that calculates and caches block hashes by number
   * following the chain for a specific branch. This is used by {@link BlockHashOperation} and
   * ensures that the correct block hash is returned even when the block being imported is on a
   * fork.
   *
   * <p>A new BlockHashCache must be created for each block being processed but should be reused for
   * all transactions within that block.
   */
  @Override
  public BlockHashLookup createBlockHashLookup(
      final Blockchain blockchain, final ProcessableBlockHeader blockHeader) {
    return new BlockchainBasedBlockHashLookup(blockHeader, blockchain);
  }
}
