/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class IstanbulGasCalculator extends ConstantinopleFixGasCalculator {

  private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);
  private static final Gas TX_DATA_NON_ZERO_COST = Gas.of(16L);
  private static final Gas TX_BASE_COST = Gas.of(21_000L);

  @Override
  public Gas transactionIntrinsicGasCost(final Transaction transaction) {
    final BytesValue payload = transaction.getPayload();
    int zeros = 0;
    for (int i = 0; i < payload.size(); i++) {
      if (payload.get(i) == 0) {
        ++zeros;
      }
    }
    final int nonZeros = payload.size() - zeros;

    Gas cost =
        Gas.ZERO
            .plus(TX_BASE_COST)
            .plus(TX_DATA_ZERO_COST.times(zeros))
            .plus(TX_DATA_NON_ZERO_COST.times(nonZeros));

    if (transaction.isContractCreation()) {
      cost = cost.plus(txCreateExtraGasCost());
    }

    return cost;
  }
}
