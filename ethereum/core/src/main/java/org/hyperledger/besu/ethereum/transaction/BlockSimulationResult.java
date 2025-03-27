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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.List;

public class BlockSimulationResult {
  final Block block;
  final BlockCallSimulationResult blockCallSimulationResult;

  public BlockSimulationResult(
      final Block block, final BlockCallSimulationResult blockCallSimulationResult) {
    this.block = block;
    this.blockCallSimulationResult = blockCallSimulationResult;
  }

  public BlockHeader getBlockHeader() {
    return block.getHeader();
  }

  public BlockBody getBlockBody() {
    return block.getBody();
  }

  public List<TransactionReceipt> getReceipts() {
    return blockCallSimulationResult.getReceipts();
  }

  public List<TransactionSimulatorResult> getTransactionSimulations() {
    return blockCallSimulationResult.getTransactionSimulationResults();
  }

  public Block getBlock() {
    return block;
  }

  public List<LogWithMetadata> getLogsWithMetadata() {
    return blockCallSimulationResult.getTransactionSimulatorResults().stream()
        .flatMap(
            transactionSimulation ->
                LogWithMetadata.generate(
                    0,
                    transactionSimulation.logs(),
                    block.getHeader().getNumber(),
                    block.getHash(),
                    transactionSimulation.result().transaction().getHash(),
                    block
                        .getBody()
                        .getTransactions()
                        .indexOf(transactionSimulation.result().transaction()),
                    false)
                    .stream())
        .toList();
  }
}
