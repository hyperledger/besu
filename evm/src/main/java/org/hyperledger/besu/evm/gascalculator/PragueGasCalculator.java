/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.gascalculator;

import static org.hyperledger.besu.datatypes.Address.BLS12_MAP_FP2_TO_G2;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

/**
 * Gas Calculator for Prague
 *
 * <UL>
 *   <LI>Gas costs for EIP-7702 (Code Delegation)
 * </UL>
 */
public class PragueGasCalculator extends CancunGasCalculator {
  private static final long TOTAL_COST_FLOOR_PER_TOKEN = 10L;

  final long existingAccountGasRefund;

  /**
   * The default mainnet target blobs per block for Prague getBlobGasPerBlob() * 6 blobs = 131072 *
   * 6 = 786432 = 0xC0000
   */
  private static final int DEFAULT_TARGET_BLOBS_PER_BLOCK_PRAGUE = 6;

  /** Instantiates a new Prague Gas Calculator. */
  public PragueGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19], DEFAULT_TARGET_BLOBS_PER_BLOCK_PRAGUE);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param targetBlobsPerBlock the target blobs per block
   */
  public PragueGasCalculator(final int targetBlobsPerBlock) {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19], targetBlobsPerBlock);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param maxPrecompile the max precompile
   * @param targetBlobsPerBlock the target blobs per block
   */
  protected PragueGasCalculator(final int maxPrecompile, final int targetBlobsPerBlock) {
    super(maxPrecompile, targetBlobsPerBlock);
    this.existingAccountGasRefund = newAccountGasCost() - CodeDelegation.PER_AUTH_BASE_COST;
  }

  @Override
  public long delegateCodeGasCost(final int delegateCodeListLength) {
    return newAccountGasCost() * delegateCodeListLength;
  }

  @Override
  public long calculateDelegateCodeGasRefund(final long alreadyExistingAccounts) {
    return existingAccountGasRefund * alreadyExistingAccounts;
  }

  @Override
  public long calculateGasRefund(
      final Transaction transaction,
      final MessageFrame initialFrame,
      final long codeDelegationRefund) {

    final long refundAllowance =
        calculateRefundAllowance(transaction, initialFrame, codeDelegationRefund);

    final long executionGasUsed =
        transaction.getGasLimit() - initialFrame.getRemainingGas() - refundAllowance;
    final long transactionFloorCost = transactionFloorCost(transaction.getPayload());
    final long totalGasUsed = Math.max(executionGasUsed, transactionFloorCost);
    return transaction.getGasLimit() - totalGasUsed;
  }

  private long calculateRefundAllowance(
      final Transaction transaction,
      final MessageFrame initialFrame,
      final long codeDelegationRefund) {
    final long selfDestructRefund =
        getSelfDestructRefundAmount() * initialFrame.getSelfDestructs().size();
    final long executionRefund =
        initialFrame.getGasRefund() + selfDestructRefund + codeDelegationRefund;
    // Integer truncation takes care of the floor calculation needed after the divide.
    final long maxRefundAllowance =
        (transaction.getGasLimit() - initialFrame.getRemainingGas()) / getMaxRefundQuotient();
    return Math.min(executionRefund, maxRefundAllowance);
  }

  @Override
  public long transactionFloorCost(final Bytes transactionPayload) {
    return clampedAdd(
        getMinimumTransactionCost(),
        tokensInCallData(transactionPayload.size(), zeroBytes(transactionPayload))
            * TOTAL_COST_FLOOR_PER_TOKEN);
  }

  private long tokensInCallData(final long payloadSize, final long zeroBytes) {
    // as defined in https://eips.ethereum.org/EIPS/eip-7623#specification
    return clampedAdd(zeroBytes, (payloadSize - zeroBytes) * 4);
  }
}
