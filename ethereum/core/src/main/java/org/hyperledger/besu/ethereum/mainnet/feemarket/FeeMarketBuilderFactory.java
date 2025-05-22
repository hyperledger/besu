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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.util.Optional;

/**
 * Factory for creating {@link FeeMarket} instances used in protocol specification configuration.
 */
public class FeeMarketBuilderFactory {
  @FunctionalInterface
  public interface FeeMarketBuilder {
    FeeMarket apply(
        long londonForkBlockNumber, Optional<Wei> baseFeePerGasOverride, BlobSchedule blobSchedule);
  }

  /**
   * Creates a {@link ProtocolSpecBuilder.FeeMarketBuilder} based on the given parameters.
   *
   * @param londonForkBlockNumber the block number of the London fork
   * @param isZeroBaseFee whether to use zero base fee market
   * @param isFixedBaseFee whether to use fixed base fee market
   * @param minTransactionGasPrice minimum gas price for transactions
   * @param feeMarketBuilder the feeMarketBuilder
   * @return a configured {@link ProtocolSpecBuilder.FeeMarketBuilder}
   */
  public static ProtocolSpecBuilder.FeeMarketBuilder createFeeMarket(
      long londonForkBlockNumber,
      boolean isZeroBaseFee,
      boolean isFixedBaseFee,
      Wei minTransactionGasPrice,
      FeeMarketBuilder feeMarketBuilder) {
    if (isZeroBaseFee) {
      return blobSchedule -> FeeMarket.zeroBaseFee(londonForkBlockNumber);
    }
    if (isFixedBaseFee) {
      return blobSchedule -> FeeMarket.fixedBaseFee(londonForkBlockNumber, minTransactionGasPrice);
    }
    return blobSchedule ->
        feeMarketBuilder.apply(
            londonForkBlockNumber, Optional.of(minTransactionGasPrice), blobSchedule);
  }
}
