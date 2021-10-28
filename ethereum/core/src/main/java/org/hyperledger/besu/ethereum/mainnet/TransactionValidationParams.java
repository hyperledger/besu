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
package org.hyperledger.besu.ethereum.mainnet;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
public interface TransactionValidationParams {

  TransactionValidationParams processingBlockParams =
      ImmutableTransactionValidationParams.of(false, false, false, true, false, false);

  TransactionValidationParams transactionPoolParams =
      ImmutableTransactionValidationParams.of(true, false, true, true, true, false);

  TransactionValidationParams miningParams =
      ImmutableTransactionValidationParams.of(false, false, false, true, true, false);

  TransactionValidationParams blockReplayParams =
      ImmutableTransactionValidationParams.of(false, false, false, false, false, false);

  TransactionValidationParams transactionSimulatorParams =
      ImmutableTransactionValidationParams.of(false, false, false, false, false, true);

  @Value.Default
  default boolean isAllowFutureNonce() {
    return false;
  }

  @Value.Default
  default boolean isAllowExceedingBalance() {
    return false;
  }

  @Value.Default
  default boolean isAllowMaxFeeGasBelowBaseFee() {
    return false;
  }

  @Value.Default
  default boolean checkOnchainPermissions() {
    return false;
  }

  @Value.Default
  default boolean checkLocalPermissions() {
    return true;
  }

  @Value.Default
  default boolean isAllowContractAddressAsSender() {
    return false;
  }

  static TransactionValidationParams transactionSimulator() {
    return transactionSimulatorParams;
  }

  static TransactionValidationParams processingBlock() {
    return processingBlockParams;
  }

  static TransactionValidationParams transactionPool() {
    return transactionPoolParams;
  }

  static TransactionValidationParams mining() {
    return miningParams;
  }

  static TransactionValidationParams blockReplay() {
    return blockReplayParams;
  }
}
