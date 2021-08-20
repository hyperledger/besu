/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.GasAndAccessedState;
import org.hyperledger.besu.ethereum.core.Transaction;

import org.apache.tuweni.bytes.Bytes;

public class FrontierTransactionGasCalculator implements TransactionGasCalculator {

  private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);

  private static final Gas TX_DATA_NON_ZERO_COST = Gas.of(68L);

  private static final Gas TX_BASE_COST = Gas.of(21_000L);

  private static final Gas TX_CREATE_EXTRA_COST = Gas.of(0L);

  private static final Gas CODE_DEPOSIT_BYTE_COST = Gas.of(200L);

  @Override
  public GasAndAccessedState transactionIntrinsicGasCostAndAccessedState(
      final Transaction transaction) {
    final Bytes payload = transaction.getPayload();
    int zeros = 0;
    for (int i = 0; i < payload.size(); i++) {
      if (payload.get(i) == 0) {
        ++zeros;
      }
    }
    final int nonZeros = payload.size() - zeros;

    Gas cost =
        TX_BASE_COST
            .plus(TX_DATA_ZERO_COST.times(zeros))
            .plus(TX_DATA_NON_ZERO_COST.times(nonZeros));

    if (transaction.isContractCreation()) {
      cost = cost.plus(txCreateExtraGasCost());
    }

    return new GasAndAccessedState(cost);
  }

  /**
   * Returns the additional gas cost for contract creation transactions
   *
   * @return the additional gas cost for contract creation transactions
   */
  protected Gas txCreateExtraGasCost() {
    return TX_CREATE_EXTRA_COST;
  }

  @Override
  public Gas codeDepositGasCost(final int codeSize) {
    return CODE_DEPOSIT_BYTE_COST.times(codeSize);
  }

  @Override
  public Gas getMaximumPmtCost() {
    // what would be the gas for PMT with hash of all non-zeros
    int nonZeros = 64;
    return TX_BASE_COST.plus(TX_DATA_NON_ZERO_COST.times(nonZeros));
  }
}
