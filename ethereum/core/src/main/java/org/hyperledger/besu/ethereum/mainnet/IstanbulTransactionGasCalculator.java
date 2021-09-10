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

public class IstanbulTransactionGasCalculator extends HomesteadTransactionGasCalculator {

  private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);
  private static final Gas ISTANBUL_TX_DATA_NON_ZERO_COST = Gas.of(16L);
  private static final Gas TX_BASE_COST = Gas.of(21_000L);

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
            .plus(ISTANBUL_TX_DATA_NON_ZERO_COST.times(nonZeros));

    if (transaction.isContractCreation()) {
      cost = cost.plus(txCreateExtraGasCost());
    }

    return new GasAndAccessedState(cost);
  }

  @Override
  public Gas getMaximumPmtCost() {
    int nonZeros = 64;
    return TX_BASE_COST.plus(ISTANBUL_TX_DATA_NON_ZERO_COST.times(nonZeros));
  }
}
