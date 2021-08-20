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

/**
 * Provides various gas cost lookups and calculations used during transaction processing outside the
 * EVM.
 *
 * <p>The {@code TransactionGasCalculator} is meant to encapsulate all {@link Gas}-related
 * calculations not needed during EVM execution or caused by EVM execution. EVM Relevant or caused
 * gas calculations live in the {@link org.hyperledger.besu.ethereum.vm.GasCalculator}. Current
 * calculations revolve around block encoding of transactions, account creation, how much refund to
 * apply, and private transaction gas reservations.
 */
public interface TransactionGasCalculator {

  /**
   * Returns a {@link Transaction}s intrinsic gas cost, i.e. the cost deriving from its encoded
   * binary representation when stored on-chain.
   *
   * @param transaction The transaction
   * @return the transaction's intrinsic gas cost
   */
  GasAndAccessedState transactionIntrinsicGasCostAndAccessedState(Transaction transaction);

  /**
   * Returns the cost for a {@link AbstractMessageProcessor} to deposit the code in storage
   *
   * @param codeSize The size of the code in bytes
   * @return the code deposit cost
   */
  Gas codeDepositGasCost(int codeSize);

  /**
   * A measure of the maximum amount of refunded gas a transaction will be credited with.
   *
   * @return the quotient of the equation `txGasCost / refundQuotient`.
   */
  default long getMaxRefundQuotient() {
    return 2;
  }

  /**
   * Maximum Cost of a Privacy Marker Transaction (PMT). See {@link
   * org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory}
   *
   * @return the maximum gas cost
   */
  // what would be the gas for a PMT with hash of all non-zeros
  Gas getMaximumPmtCost();
}
