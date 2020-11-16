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
package org.hyperledger.besu.ethereum.core.fees;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.transaction.EIP1559Transaction;
import org.hyperledger.besu.ethereum.core.transaction.FrontierTransaction;
import org.hyperledger.besu.plugin.data.TypedTransaction;

import java.math.BigInteger;

@FunctionalInterface
public interface TransactionPriceCalculator {
  Wei price(TypedTransaction transaction, Long baseFee);

  static TransactionPriceCalculator frontier() {
    return (transaction, baseFee) -> ((FrontierTransaction) transaction).getGasPrice();
  }

  static TransactionPriceCalculator eip1559() {
    return (transaction, baseFee) -> {
      if (transaction instanceof FrontierTransaction) {
        return ((FrontierTransaction) transaction).getGasPrice();
      } else if (transaction instanceof EIP1559Transaction) {
        final EIP1559Transaction eip1559Transaction = (EIP1559Transaction) transaction;
        ExperimentalEIPs.eip1559MustBeEnabled();
        final Wei gasPremium = Wei.of((BigInteger) eip1559Transaction.getGasPremium().getValue());
        final Wei feeCap = Wei.of((BigInteger) eip1559Transaction.getFeeCap().getValue());
        Wei price = gasPremium.add(baseFee);
        if (price.compareTo(feeCap) > 0) {
          price = feeCap;
        }
        return price;
      } else {
        throw new IllegalStateException();
      }
    };
  }
}
