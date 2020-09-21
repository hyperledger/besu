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

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Words;
import org.hyperledger.besu.ethereum.vm.operations.ExpOperation;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class FrontierGasCalculator implements GasCalculator {

  private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);

  private static final Gas TX_DATA_NON_ZERO_COST = Gas.of(68L);

  private static final Gas TX_BASE_COST = Gas.of(21_000L);

  private static final Gas TX_CREATE_EXTRA_COST = Gas.of(0L);

  private static final Gas CODE_DEPOSIT_BYTE_COST = Gas.of(200L);

  private static final Gas ID_PRECOMPILED_BASE_GAS_COST = Gas.of(15L);

  private static final Gas ID_PRECOMPILED_WORD_GAS_COST = Gas.of(3L);

  private static final Gas ECREC_PRECOMPILED_GAS_COST = Gas.of(3_000L);

  private static final Gas SHA256_PRECOMPILED_BASE_GAS_COST = Gas.of(60L);

  private static final Gas SHA256_PRECOMPILED_WORD_GAS_COST = Gas.of(12L);

  private static final Gas RIPEMD160_PRECOMPILED_WORD_GAS_COST = Gas.of(120L);

  private static final Gas RIPEMD160_PRECOMPILED_BASE_GAS_COST = Gas.of(600L);

  private static final Gas VERY_LOW_TIER_GAS_COST = Gas.of(3L);

  private static final Gas LOW_TIER_GAS_COST = Gas.of(5L);

  private static final Gas BASE_TIER_GAS_COST = Gas.of(2L);

  private static final Gas MID_TIER_GAS_COST = Gas.of(8L);

  private static final Gas HIGH_TIER_GAS_COST = Gas.of(10L);

  private static final Gas CALL_OPERATION_BASE_GAS_COST = Gas.of(40L);

  private static final Gas CALL_VALUE_TRANSFER_GAS_COST = Gas.of(9_000L);

  private static final Gas ADDITIONAL_CALL_STIPEND = Gas.of(2_300L);

  private static final Gas NEW_ACCOUNT_GAS_COST = Gas.of(25_000L);

  private static final Gas CREATE_OPERATION_GAS_COST = Gas.of(32_000L);

  private static final Gas COPY_WORD_GAS_COST = Gas.of(3L);

  private static final Gas MEMORY_WORD_GAS_COST = Gas.of(3L);

  private static final Gas BALANCE_OPERATION_GAS_COST = Gas.of(20L);

  private static final Gas BLOCKHASH_OPERATION_GAS_COST = Gas.of(20L);

  private static final Gas EXP_OPERATION_BASE_GAS_COST = Gas.of(10);

  private static final Gas EXP_OPERATION_BYTE_GAS_COST = Gas.of(10);

  private static final Gas EXT_CODE_BASE_GAS_COST = Gas.of(20L);

  private static final Gas JUMPDEST_OPERATION_GAS_COST = Gas.of(1);

  private static final Gas LOG_OPERATION_BASE_GAS_COST = Gas.of(375L);

  private static final Gas LOG_OPERATION_DATA_BYTE_GAS_COST = Gas.of(8L);

  private static final Gas LOG_OPERATION_TOPIC_GAS_COST = Gas.of(375L);

  private static final Gas SELFDESTRUCT_OPERATION_GAS_COST = Gas.of(0);

  private static final Gas SHA3_OPERATION_BASE_GAS_COST = Gas.of(30L);

  static final Gas SHA3_OPERATION_WORD_GAS_COST = Gas.of(6L);

  private static final Gas SLOAD_OPERATION_GAS_COST = Gas.of(50);

  private static final Gas STORAGE_SET_GAS_COST = Gas.of(20_000L);

  private static final Gas STORAGE_RESET_GAS_COST = Gas.of(5_000L);

  private static final Gas STORAGE_RESET_REFUND_AMOUNT = Gas.of(15_000L);

  private static final Gas SELF_DESTRUCT_REFUND_AMOUNT = Gas.of(24_000L);

  @Override
  public Gas transactionIntrinsicGasCost(final Transaction transaction) {
    final Bytes payload = transaction.getPayload();
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

  /**
   * Returns the additional gas cost for contract creation transactions
   *
   * @return the additional gas cost for contract creation transactions
   */
  protected Gas txCreateExtraGasCost() {
    return TX_CREATE_EXTRA_COST;
  }

  @Override
  public Gas codeDepositGasCost(final int codeSize) {
    return CODE_DEPOSIT_BYTE_COST.times(codeSize);
  }

  @Override
  public Gas idPrecompiledContractGasCost(final Bytes input) {
    return ID_PRECOMPILED_WORD_GAS_COST
        .times(Words.numWords(input))
        .plus(ID_PRECOMPILED_BASE_GAS_COST);
  }

  @Override
  public Gas getEcrecPrecompiledContractGasCost() {
    return ECREC_PRECOMPILED_GAS_COST;
  }

  @Override
  public Gas sha256PrecompiledContractGasCost(final Bytes input) {
    return SHA256_PRECOMPILED_WORD_GAS_COST
        .times(Words.numWords(input))
        .plus(SHA256_PRECOMPILED_BASE_GAS_COST);
  }

  @Override
  public Gas ripemd160PrecompiledContractGasCost(final Bytes input) {
    return RIPEMD160_PRECOMPILED_WORD_GAS_COST
        .times(Words.numWords(input))
        .plus(RIPEMD160_PRECOMPILED_BASE_GAS_COST);
  }

  @Override
  public Gas getZeroTierGasCost() {
    return Gas.ZERO;
  }

  @Override
  public Gas getVeryLowTierGasCost() {
    return VERY_LOW_TIER_GAS_COST;
  }

  @Override
  public Gas getLowTierGasCost() {
    return LOW_TIER_GAS_COST;
  }

  @Override
  public Gas getBaseTierGasCost() {
    return BASE_TIER_GAS_COST;
  }

  @Override
  public Gas getMidTierGasCost() {
    return MID_TIER_GAS_COST;
  }

  @Override
  public Gas getHighTierGasCost() {
    return HIGH_TIER_GAS_COST;
  }

  /**
   * Returns the base gas cost to execute a call operation.
   *
   * @return the base gas cost to execute a call operation
   */
  protected Gas callOperationBaseGasCost() {
    return CALL_OPERATION_BASE_GAS_COST;
  }

  /**
   * Returns the gas cost to transfer funds in a call operation.
   *
   * @return the gas cost to transfer funds in a call operation
   */
  Gas callValueTransferGasCost() {
    return CALL_VALUE_TRANSFER_GAS_COST;
  }

  /**
   * Returns the gas cost to create a new account.
   *
   * @return the gas cost to create a new account
   */
  Gas newAccountGasCost() {
    return NEW_ACCOUNT_GAS_COST;
  }

  @Override
  public Gas callOperationGasCost(
      final MessageFrame frame,
      final Gas stipend,
      final UInt256 inputDataOffset,
      final UInt256 inputDataLength,
      final UInt256 outputDataOffset,
      final UInt256 outputDataLength,
      final Wei transferValue,
      final Account recipient) {
    final Gas inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final Gas outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final Gas memoryExpansionCost = inputDataMemoryExpansionCost.max(outputDataMemoryExpansionCost);

    Gas cost = callOperationBaseGasCost().plus(stipend).plus(memoryExpansionCost);

    if (!transferValue.isZero()) {
      cost = cost.plus(callValueTransferGasCost());
    }

    if (recipient == null) {
      cost = cost.plus(newAccountGasCost());
    }

    return cost;
  }

  /**
   * Returns the additional call stipend for calls with value transfers.
   *
   * @return the additional call stipend for calls with value transfers
   */
  @Override
  public Gas getAdditionalCallStipend() {
    return ADDITIONAL_CALL_STIPEND;
  }

  @Override
  public Gas gasAvailableForChildCall(
      final MessageFrame frame, final Gas stipend, final boolean transfersValue) {
    if (transfersValue) {
      return stipend.plus(getAdditionalCallStipend());
    } else {
      return stipend;
    }
  }

  @Override
  public Gas createOperationGasCost(final MessageFrame frame) {
    final UInt256 initCodeOffset = UInt256.fromBytes(frame.getStackItem(1));
    final UInt256 initCodeLength = UInt256.fromBytes(frame.getStackItem(2));

    final Gas memoryGasCost = memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
    return CREATE_OPERATION_GAS_COST.plus(memoryGasCost);
  }

  @Override
  public Gas gasAvailableForChildCreate(final Gas stipend) {
    return stipend;
  }

  @Override
  public Gas dataCopyOperationGasCost(
      final MessageFrame frame, final UInt256 offset, final UInt256 length) {
    return copyWordsToMemoryGasCost(
        frame, VERY_LOW_TIER_GAS_COST, COPY_WORD_GAS_COST, offset, length);
  }

  @Override
  public Gas memoryExpansionGasCost(
      final MessageFrame frame, final UInt256 offset, final UInt256 length) {

    final Gas pre = memoryCost(frame.memoryWordSize());
    final Gas post = memoryCost(frame.calculateMemoryExpansion(offset, length));

    return post.minus(pre);
  }

  @Override
  public Gas getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  @Override
  public Gas getBlockHashOperationGasCost() {
    return BLOCKHASH_OPERATION_GAS_COST;
  }

  /**
   * Returns the gas cost for a byte in the {@link ExpOperation}.
   *
   * @return the gas cost for a byte in the exponent operation
   */
  protected Gas expOperationByteGasCost() {
    return EXP_OPERATION_BYTE_GAS_COST;
  }

  @Override
  public Gas expOperationGasCost(final int numBytes) {
    return expOperationByteGasCost().times(Gas.of(numBytes)).plus(EXP_OPERATION_BASE_GAS_COST);
  }

  /**
   * Returns the base gas cost for external code accesses.
   *
   * @return the base gas cost for external code accesses
   */
  protected Gas extCodeBaseGasCost() {
    return EXT_CODE_BASE_GAS_COST;
  }

  @Override
  public Gas extCodeCopyOperationGasCost(
      final MessageFrame frame, final UInt256 offset, final UInt256 length) {
    return copyWordsToMemoryGasCost(
        frame, extCodeBaseGasCost(), COPY_WORD_GAS_COST, offset, length);
  }

  @Override
  public Gas extCodeHashOperationGasCost() {
    throw new UnsupportedOperationException(
        "EXTCODEHASH not supported by " + getClass().getSimpleName());
  }

  @Override
  public Gas getExtCodeSizeOperationGasCost() {
    return extCodeBaseGasCost();
  }

  @Override
  public Gas getJumpDestOperationGasCost() {
    return JUMPDEST_OPERATION_GAS_COST;
  }

  @Override
  public Gas logOperationGasCost(
      final MessageFrame frame,
      final UInt256 dataOffset,
      final UInt256 dataLength,
      final int numTopics) {
    return Gas.ZERO
        .plus(LOG_OPERATION_BASE_GAS_COST)
        .plus(LOG_OPERATION_DATA_BYTE_GAS_COST.times(Gas.of(dataLength)))
        .plus(LOG_OPERATION_TOPIC_GAS_COST.times(numTopics))
        .plus(memoryExpansionGasCost(frame, dataOffset, dataLength));
  }

  @Override
  public Gas mLoadOperationGasCost(final MessageFrame frame, final UInt256 offset) {
    return VERY_LOW_TIER_GAS_COST.plus(memoryExpansionGasCost(frame, offset, UInt256.valueOf(32)));
  }

  @Override
  public Gas mStoreOperationGasCost(final MessageFrame frame, final UInt256 offset) {
    return VERY_LOW_TIER_GAS_COST.plus(memoryExpansionGasCost(frame, offset, UInt256.valueOf(32)));
  }

  @Override
  public Gas mStore8OperationGasCost(final MessageFrame frame, final UInt256 offset) {
    return VERY_LOW_TIER_GAS_COST.plus(memoryExpansionGasCost(frame, offset, UInt256.ONE));
  }

  @Override
  public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    return SELFDESTRUCT_OPERATION_GAS_COST;
  }

  @Override
  public Gas sha3OperationGasCost(
      final MessageFrame frame, final UInt256 offset, final UInt256 length) {
    return copyWordsToMemoryGasCost(
        frame, SHA3_OPERATION_BASE_GAS_COST, SHA3_OPERATION_WORD_GAS_COST, offset, length);
  }

  @Override
  public Gas create2OperationGasCost(final MessageFrame frame) {
    throw new UnsupportedOperationException(
        "CREATE2 operation not supported by " + getClass().getSimpleName());
  }

  @Override
  public Gas getSloadOperationGasCost() {
    return SLOAD_OPERATION_GAS_COST;
  }

  @Override
  public Gas calculateStorageCost(
      final Account account, final UInt256 key, final UInt256 newValue) {
    return !newValue.isZero() && account.getStorageValue(key).isZero()
        ? STORAGE_SET_GAS_COST
        : STORAGE_RESET_GAS_COST;
  }

  @Override
  public Gas calculateStorageRefundAmount(
      final Account account, final UInt256 key, final UInt256 newValue) {
    return newValue.isZero() && !account.getStorageValue(key).isZero()
        ? STORAGE_RESET_REFUND_AMOUNT
        : Gas.ZERO;
  }

  @Override
  public Gas getSelfDestructRefundAmount() {
    return SELF_DESTRUCT_REFUND_AMOUNT;
  }

  @Override
  public Gas getBeginSubGasCost() {
    throw new UnsupportedOperationException(
        "BEGINSUB operation not supported by " + getClass().getSimpleName());
  }

  protected Gas copyWordsToMemoryGasCost(
      final MessageFrame frame,
      final Gas baseGasCost,
      final Gas wordGasCost,
      final UInt256 offset,
      final UInt256 length) {
    final UInt256 numWords = length.divideCeil(Bytes32.SIZE);

    final Gas copyCost = wordGasCost.times(Gas.of(numWords)).plus(baseGasCost);
    final Gas memoryCost = memoryExpansionGasCost(frame, offset, length);

    return copyCost.plus(memoryCost);
  }

  private static Gas memoryCost(final UInt256 length) {
    if (!length.fitsInt()) {
      return Gas.MAX_VALUE;
    }
    final Gas len = Gas.of(length);
    final Gas base = len.times(len).dividedBy(512);

    return MEMORY_WORD_GAS_COST.times(len).plus(base);
  }
}
