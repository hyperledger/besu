package org.hyperledger.besu.evm.gascalculator;
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
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.BALANCE_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_KECCAK_LEAF_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.List;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Shanghai gas calculator. */
public class ShanghaiGasCalculator extends LondonGasCalculator {

  private static final long INIT_CODE_COST = 2L;

  private static final long CREATE_OPERATION_GAS_COST = 1_000L;

  /**
   * Instantiates a new ShanghaiGasCalculator
   *
   * @param maxPrecompile the max precompile
   */
  protected ShanghaiGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  /** Instantiates a new ShanghaiGasCalculator */
  public ShanghaiGasCalculator() {
    super();
  }

  @Override
  public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreation) {
    long intrinsicGasCost = super.transactionIntrinsicGasCost(payload, isContractCreation);
    if (isContractCreation) {
      return clampedAdd(intrinsicGasCost, calculateInitGasCost(payload.size()));
    } else {
      return intrinsicGasCost;
    }
  }

  @Override
  public long computeBaseAccessEventsCost(
      final AccessWitness accessWitness,
      final Transaction transaction,
      final MutableAccount sender) {
    final boolean sendsValue = !transaction.getValue().equals(Wei.ZERO);
    long cost = 0;
    cost += accessWitness.touchTxOriginAndComputeGas(transaction.getSender());

    if (transaction.getTo().isPresent()) {
      final Address to = transaction.getTo().get();
      cost += accessWitness.touchTxExistingAndComputeGas(to, sendsValue);
    } else {
      cost +=
          accessWitness.touchAndChargeContractCreateInit(
              Address.contractAddress(transaction.getSender(), sender.getNonce() - 1L), sendsValue);
    }

    return cost;
  }

  @Override
  public long initCreateContractGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAndChargeContractCreateInit(frame.getContractAddress(), !frame.getValue().isZero());
  }

  @Override
  public long completedCreateContractGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAndChargeContractCreateCompleted(frame.getContractAddress());
  }

  @Override
  public long codeDepositGasCost(final MessageFrame frame, final int codeSize) {
    long cost = super.codeDepositGasCost(frame, codeSize);
    return clampedAdd(
        cost,
        frame
            .getAccessWitness()
            .touchCodeChunksUponContractCreation(frame.getContractAddress(), codeSize));
  }

  @Override
  public long createOperationGasCost(final MessageFrame frame) {

    final long initCodeOffset = clampedToLong(frame.getStackItem(1));
    final long initCodeLength = clampedToLong(frame.getStackItem(2));

    final long memoryGasCost = memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
    long gasCost = clampedAdd(CREATE_OPERATION_GAS_COST, memoryGasCost);

    return clampedAdd(gasCost, calculateInitGasCost(initCodeLength));
  }

  private static long calculateInitGasCost(final long initCodeLength) {
    final int dataLength = (int) Math.ceil(initCodeLength / 32.0);
    return dataLength * INIT_CODE_COST;
  }

  @Override
  public long callOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address to) {

    final long baseCost =
        super.callOperationGasCost(
            frame,
            stipend,
            inputDataOffset,
            inputDataLength,
            outputDataOffset,
            outputDataLength,
            transferValue,
            recipient,
            to);
    long cost = baseCost;
    if (frame.getWorldUpdater().get(to) == null) {
      cost = clampedAdd(baseCost, frame.getAccessWitness().touchAndChargeProofOfAbsence(to));
    } else {
      if (!super.isPrecompile(to)) {
        cost = clampedAdd(baseCost, frame.getAccessWitness().touchAndChargeMessageCall(to));
      }
    }

    if (!transferValue.isZero()) {
      cost =
          clampedAdd(
              baseCost,
              frame.getAccessWitness().touchAndChargeValueTransfer(recipient.getAddress(), to));
    }
    return cost;
  }

  @Override
  public long calculateStorageCost(
      final MessageFrame frame,
      final UInt256 key,
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    long gasCost = 0;

    // TODO VEKLE: right now we're not computing what is the tree index and subindex we're just
    // charging the cost of writing to the storage
    AccessWitness accessWitness = frame.getAccessWitness();
    List<UInt256> treeIndexes = accessWitness.getStorageSlotTreeIndexes(key);
    gasCost +=
        frame
            .getAccessWitness()
            .touchAddressOnReadAndComputeGas(
                frame.getRecipientAddress(), treeIndexes.get(0), treeIndexes.get(1));
    gasCost +=
        frame
            .getAccessWitness()
            .touchAddressOnWriteAndComputeGas(
                frame.getRecipientAddress(), treeIndexes.get(0), treeIndexes.get(1));

    if (gasCost == 0) {
      gasCost = WARM_STORAGE_READ_COST;
    }
    return gasCost;
  }

  @Override
  public long getSloadOperationGasCost(final MessageFrame frame, final UInt256 key) {
    AccessWitness accessWitness = frame.getAccessWitness();
    List<UInt256> treeIndexes = accessWitness.getStorageSlotTreeIndexes(key);
    long gasCost =
        frame
            .getAccessWitness()
            .touchAddressOnReadAndComputeGas(
                frame.getContractAddress(), treeIndexes.get(0), treeIndexes.get(1));
    if (gasCost == 0) {
      gasCost = WARM_STORAGE_READ_COST;
    }
    return gasCost;
  }

  @Override
  public long getBalanceOperationGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAddressOnWriteAndComputeGas(
            frame.getContractAddress(), UInt256.ZERO, BALANCE_LEAF_KEY);
  }

  @Override
  public long extCodeHashOperationGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAddressOnReadAndComputeGas(
            frame.getContractAddress(), UInt256.ZERO, CODE_KECCAK_LEAF_KEY);
  }

  @Override
  public long calculateStorageRefundAmount(
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {
    return 0L;
  }

  @Override
  public long selfDestructOperationGasCost(
      final MessageFrame frame,
      final Account recipient,
      final Wei inheritance,
      final Address originatorAddress) {
    long cost =
        super.selfDestructOperationGasCost(frame, recipient, inheritance, originatorAddress);
    cost =
        clampedAdd(
            cost,
            frame
                .getAccessWitness()
                .touchAddressOnReadAndComputeGas(
                    originatorAddress, UInt256.ZERO, BALANCE_LEAF_KEY));
    if (recipient != null) {
      cost =
          clampedAdd(
              cost,
              frame
                  .getAccessWitness()
                  .touchAddressOnReadAndComputeGas(
                      recipient.getAddress(), UInt256.ZERO, BALANCE_LEAF_KEY));
    }
    return cost;
  }
}
