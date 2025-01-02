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
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;

public class BlockSimulationResult {
  final Block block;
  final List<TransactionReceipt> receipts;
  List<TransactionSimulatorResult> transactionSimulationResults;

  public BlockSimulationResult(
      final Block block,
      final List<? extends TransactionReceipt> receipts,
      final List<TransactionSimulatorResult> transactionSimulationResults) {
    this.block = block;
    this.receipts = new ArrayList<>(receipts);
    this.transactionSimulationResults = transactionSimulationResults;
  }

  public BlockHeader getBlockHeader() {
    return block.getHeader();
  }

  public BlockBody getBlockBody() {
    return block.getBody();
  }

  public List<? extends TransactionReceipt> getReceipts() {
    return receipts;
  }

  public List<TransactionSimulatorResult> getTransactionSimulations() {
    return transactionSimulationResults;
  }

  public Block getBlock() {
    return block;
  }
}
