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
package org.hyperledger.besu.plugin.data;

import java.util.List;

/** This class represents the result of simulating the processing of a block. */
public class PluginBlockSimulationResult {
  final BlockHeader blockHeader;
  final BlockBody blockBody;
  final List<? extends TransactionReceipt> receipts;
  final List<TransactionSimulationResult> transactionSimulationResults;

  /**
   * Constructs a new BlockSimulationResult instance.
   *
   * @param blockHeader the block header
   * @param blockBody the block body
   * @param receipts the list of transaction receipts
   * @param transactionSimulationResults the list of transaction simulation results
   */
  public PluginBlockSimulationResult(
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final List<? extends TransactionReceipt> receipts,
      final List<TransactionSimulationResult> transactionSimulationResults) {
    this.blockHeader = blockHeader;
    this.blockBody = blockBody;
    this.receipts = receipts;
    this.transactionSimulationResults = transactionSimulationResults;
  }

  /**
   * Gets the block header.
   *
   * @return the block header
   */
  public BlockHeader getBlockHeader() {
    return blockHeader;
  }

  /**
   * Gets the block body.
   *
   * @return the block body
   */
  public BlockBody getBlockBody() {
    return blockBody;
  }

  /**
   * Gets the list of transaction receipts.
   *
   * @return the list of transaction receipts
   */
  public List<? extends TransactionReceipt> getReceipts() {
    return receipts;
  }

  /**
   * Gets the list of transaction simulation results.
   *
   * @return the list of transaction simulation results
   */
  public List<TransactionSimulationResult> getTransactionSimulationResults() {
    return transactionSimulationResults;
  }
}
