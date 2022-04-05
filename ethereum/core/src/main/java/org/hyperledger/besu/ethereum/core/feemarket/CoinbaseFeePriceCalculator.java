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
package org.hyperledger.besu.ethereum.core.feemarket;

import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

@FunctionalInterface
public interface CoinbaseFeePriceCalculator {
  Wei price(long coinbaseFee, Wei transactionGasPrice, Optional<Wei> baseFee);

  static CoinbaseFeePriceCalculator frontier() {
    return (coinbaseFee, transactionGasPrice, baseFee) -> transactionGasPrice.multiply(coinbaseFee);
  }

  static CoinbaseFeePriceCalculator eip1559() {
    return (coinbaseFee, transactionGasPrice, baseFee) -> {
      return transactionGasPrice.subtract(baseFee.orElseThrow()).multiply(coinbaseFee);
    };
  }
}
