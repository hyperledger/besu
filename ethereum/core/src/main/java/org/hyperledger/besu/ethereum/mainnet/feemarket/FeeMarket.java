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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.feemarket.TransactionPriceCalculator;

import java.util.Optional;

public interface FeeMarket {

  default boolean implementsBaseFee() {
    return false;
  }

  default boolean implementsDataFee() {
    return false;
  }

  TransactionPriceCalculator getTransactionPriceCalculator();

  boolean satisfiesFloorTxFee(Transaction txn);

  static BaseFeeMarket london(final long londonForkBlockNumber) {
    return london(londonForkBlockNumber, Optional.empty());
  }

  static BaseFeeMarket london(
      final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
    return new LondonFeeMarket(londonForkBlockNumber, baseFeePerGasOverride);
  }

  static BaseFeeMarket cancun(
      final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
    return new CancunFeeMarket(londonForkBlockNumber, baseFeePerGasOverride);
  }

  static BaseFeeMarket cancun(
      final long londonForkBlockNumber,
      final Optional<Wei> baseFeePerGasOverride,
      final long baseFeeUpdateFraction) {
    return new CancunFeeMarket(londonForkBlockNumber, baseFeePerGasOverride, baseFeeUpdateFraction);
  }

  static BaseFeeMarket prague(
      final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
    return new PragueFeeMarket(londonForkBlockNumber, baseFeePerGasOverride);
  }

  static BaseFeeMarket prague(
      final long londonForkBlockNumber,
      final Optional<Wei> baseFeePerGasOverride,
      final long baseFeeUpdateFraction) {
    return new PragueFeeMarket(londonForkBlockNumber, baseFeePerGasOverride, baseFeeUpdateFraction);
  }

  static BaseFeeMarket zeroBaseFee(final long londonForkBlockNumber) {
    return new ZeroBaseFeeMarket(londonForkBlockNumber);
  }

  static BaseFeeMarket fixedBaseFee(final long londonForkBlockNumber, final Wei fixedBaseFee) {
    return new FixedBaseFeeMarket(londonForkBlockNumber, fixedBaseFee);
  }

  static FeeMarket legacy() {
    return new LegacyFeeMarket();
  }

  default Wei blobGasPricePerGas(final BlobGas excessBlobGas) {
    return Wei.ZERO;
  }
}
