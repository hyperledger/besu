/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

public class ZeroBaseFeeMarket extends LondonFeeMarket {

  public ZeroBaseFeeMarket(final long londonForkBlockNumber) {
    super(londonForkBlockNumber, Optional.of(Wei.ZERO));
  }

  @Override
  public Wei computeBaseFee(
      final long blockNumber,
      final Wei parentBaseFee,
      final long parentBlockGasUsed,
      final long targetGasUsed) {

    return Wei.ZERO;
  }

  @Override
  public ValidationMode baseFeeValidationMode(final long blockNumber) {
    return ValidationMode.NONE;
  }
}
