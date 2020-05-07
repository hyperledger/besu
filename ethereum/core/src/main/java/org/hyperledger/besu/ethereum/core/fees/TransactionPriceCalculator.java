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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;

import java.math.BigInteger;
import java.util.Optional;

@FunctionalInterface
public interface TransactionPriceCalculator {
  Wei price(Transaction transaction, Optional<Long> baseFee);

  static TransactionPriceCalculator frontier() {
    return (transaction, baseFee) -> transaction.getGasPrice();
  }

  static TransactionPriceCalculator eip1559() {
    return (transaction, maybeBaseFee) -> {
      ExperimentalEIPs.eip1559MustBeEnabled();
      final Wei gasPremium =
          Wei.of((BigInteger) transaction.getGasPremium().orElseThrow().getValue());
      final Wei feeCap = Wei.of((BigInteger) transaction.getFeeCap().orElseThrow().getValue());
      final Wei baseFee = Wei.of(maybeBaseFee.orElseThrow());
      Wei price = gasPremium.add(baseFee);
      if (price.compareTo(feeCap) > 0) {
        price = feeCap;
      }
      return price;
    };
  }
}
