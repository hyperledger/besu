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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Wei;

import java.util.List;

public class BlockValueCalculator {

  public static Wei calculateBlockValue(final BlockWithReceipts blockWithReceipts) {
    final Block block = blockWithReceipts.getBlock();
    final List<Transaction> txs = block.getBody().getTransactions();
    final List<TransactionReceipt> receipts = blockWithReceipts.getReceipts();
    Wei totalFee = Wei.ZERO;
    for (int i = 0; i < txs.size(); i++) {
      final Wei minerFee = txs.get(i).getEffectivePriorityFeePerGas(block.getHeader().getBaseFee());
      // we don't store gasUsed and need to calculate that on the fly
      // receipts are fetched in ascending sorted by cumulativeGasUsed
      long gasUsed = receipts.get(i).getCumulativeGasUsed();
      if (i > 0) {
        gasUsed = gasUsed - receipts.get(i - 1).getCumulativeGasUsed();
      }
      totalFee = totalFee.add(minerFee.multiply(gasUsed));
    }
    return totalFee;
  }
}
