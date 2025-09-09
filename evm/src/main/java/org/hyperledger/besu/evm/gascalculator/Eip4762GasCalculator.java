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

import static org.hyperledger.besu.ethereum.stateless.util.Parameters.BASIC_DATA_LEAF_KEY;
import static org.hyperledger.besu.ethereum.stateless.util.Parameters.CODE_HASH_LEAF_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.stateless.Eip4762AccessWitness;

import java.util.function.Supplier;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * Gas Calculator as per EIP-4762
 *
 * <UL>
 *   <LI>Gas costs for EIP-4762 (Stateless trie)
 * </UL>
 */
public class Eip4762GasCalculator extends CancunGasCalculator {
  private static final long CREATE_OPERATION_GAS_COST = 1_000L;

  /** Instantiates a new EIP-4762 Gas Calculator. */
  public Eip4762GasCalculator() {}

  @Override
  public long getColdSloadCost() {
    return 0; // no cold gas cost after verkle
  }

  @Override
  public long getColdAccountAccessCost() {
    return 0; // no cold gas cost after verkle
  }

  @Override
  public long proofOfAbsenceCost(final MessageFrame frame, final Address address) {
    return frame.getAccessWitness().touchAndChargeProofOfAbsence(address, frame.getRemainingGas());
  }

  @Override
  public long callCodeOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address contract,
      final boolean accountIsWarm) {
    return callOperationGasCost(
        frame,
        stipend,
        inputDataOffset,
        inputDataLength,
        outputDataOffset,
        outputDataLength,
        // CALLCODE transfers value to itself so hardcode it to zero
        Wei.ZERO,
        recipient,
        contract,
        accountIsWarm);
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
      final Address to,
      final boolean accountIsWarm) {

    long gas =
        super.callOperationGasCost(
            frame,
            stipend,
            inputDataOffset,
            inputDataLength,
            outputDataOffset,
            outputDataLength,
            transferValue,
            recipient,
            to,
            false);

    if (!transferValue.isZero()) {
      gas =
          clampedAdd(
              gas,
              frame
                  .getAccessWitness()
                  .touchAndChargeValueTransfer(
                      frame.getContractAddress(),
                      to,
                      recipient == null,
                      getWarmStorageReadCost(),
                      frame.getRemainingGas()));
      return gas;
    }

    if (isPrecompile(to)) {
      return clampedAdd(gas, getWarmStorageReadCost());
    }

    long messageCallGas =
        frame
            .getAccessWitness()
            .touchAddressAndChargeRead(to, BASIC_DATA_LEAF_KEY, frame.getRemainingGas());
    if (messageCallGas == 0) {
      messageCallGas = getWarmStorageReadCost();
    }

    return clampedAdd(gas, messageCallGas);
  }

  @Override
  public long callValueTransferGasCost() {
    return 0L;
  }

  @Override
  public long txCreateCost() {
    return CREATE_OPERATION_GAS_COST;
  }

  @Override
  public long codeDepositGasCost(final MessageFrame frame, final int codeSize) {
    return frame
        .getAccessWitness()
        .touchCodeChunksUponContractCreation(
            frame.getContractAddress(), codeSize, frame.getRemainingGas());
  }

  @Override
  public long calculateStorageRefundAmount(
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {
    return 0L;
  }

  @Override
  public long extCodeCopyOperationGasCost(
      final MessageFrame frame,
      final Address address,
      final boolean accountIsWarm,
      final long memOffset,
      final long codeOffset,
      final long readSize,
      final long codeSize) {
    long gas = extCodeCopyOperationGasCost(frame, memOffset, readSize);

    gas =
        clampedAdd(
            gas,
            frame
                .getAccessWitness()
                .touchCodeChunks(
                    address, false, codeOffset, readSize, codeSize, frame.getRemainingGas()));

    if (isPrecompile(address)) {
      return clampedAdd(gas, getWarmStorageReadCost());
    }

    long readTargetGas =
        frame
            .getAccessWitness()
            .touchAddressAndChargeRead(address, BASIC_DATA_LEAF_KEY, frame.getRemainingGas());
    if (readTargetGas == 0) {
      readTargetGas = getWarmStorageReadCost();
    }

    return clampedAdd(gas, readTargetGas);
  }

  @Override
  public long codeCopyOperationGasCost(
      final MessageFrame frame,
      final long memOffset,
      final long codeOffset,
      final long readSize,
      final long codeSize) {
    long gas = super.dataCopyOperationGasCost(frame, memOffset, readSize);

    final Address contractAddress = frame.getContractAddress();
    gas =
        clampedAdd(
            gas,
            frame
                .getAccessWitness()
                .touchCodeChunks(
                    contractAddress,
                    frame.wasCreatedInTransaction(contractAddress),
                    codeOffset,
                    readSize,
                    codeSize,
                    frame.getRemainingGas()));

    return gas;
  }

  @Override
  public long pushOperationGasCost(
      final MessageFrame frame, final long codeOffset, final long readSize, final long codeSize) {
    long gas = super.pushOperationGasCost(frame, codeOffset, readSize, codeSize);

    if (frame.wasCreatedInTransaction(frame.getContractAddress())
        // PUSH1 only touches code chunks if borderline
        || (readSize == 1 && codeOffset % 31 != 0)) {
      return gas;
    }

    final Address contractAddress = frame.getContractAddress();
    gas =
        clampedAdd(
            gas,
            frame
                .getAccessWitness()
                .touchCodeChunks(
                    contractAddress,
                    frame.wasCreatedInTransaction(contractAddress),
                    codeOffset,
                    readSize,
                    codeSize,
                    frame.getRemainingGas()));

    return gas;
  }

  @Override
  public long balanceOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Address address) {
    final long gas =
        frame
            .getAccessWitness()
            .touchAddressAndChargeRead(address, BASIC_DATA_LEAF_KEY, frame.getRemainingGas());
    if (gas == 0) {
      return getWarmStorageReadCost();
    }
    return gas;
  }

  @Override
  public long extCodeHashOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Address address) {
    if (isPrecompile(address)) {
      return getWarmStorageReadCost();
    }

    long gas =
        frame
            .getAccessWitness()
            .touchAddressAndChargeRead(address, CODE_HASH_LEAF_KEY, frame.getRemainingGas());
    if (gas == 0) {
      return getWarmStorageReadCost();
    }
    return gas;
  }

  @Override
  public long extCodeSizeOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Address address) {
    if (isPrecompile(address)) {
      return getWarmStorageReadCost();
    }

    long gas =
        frame
            .getAccessWitness()
            .touchAddressAndChargeRead(address, BASIC_DATA_LEAF_KEY, frame.getRemainingGas());
    if (gas == 0) {
      return getWarmStorageReadCost();
    }
    return gas;
  }

  @Override
  public long selfDestructOperationGasCost(
      final MessageFrame frame,
      final Account recipient,
      final Address recipientAddress,
      final Wei value,
      final Address originatorAddress) {
    //    TODO: only charge non-account creation for devnet-7. Geth has removed the 25k gas cost
    // from EIP-150 for account
    //     creation by mistake and will change in the next devnet
    //    long gas = super.selfDestructOperationGasCost(
    //            frame, recipient, recipientAddress, value, originatorAddress);
    long gas = 5000L;

    if (!value.isZero()) {
      gas =
          clampedAdd(
              gas,
              frame
                  .getAccessWitness()
                  .touchAndChargeValueTransferSelfDestruct(
                      originatorAddress,
                      recipientAddress,
                      recipient == null,
                      getWarmStorageReadCost(),
                      frame.getRemainingGas()));
    }

    // check if there's any balance in the originating account in search of value
    gas =
        clampedAdd(
            gas,
            frame
                .getAccessWitness()
                .touchAddressAndChargeRead(
                    originatorAddress, BASIC_DATA_LEAF_KEY, frame.getRemainingGas()));

    // TODO: REMOVE - if code removed below there's no point to check for this
    if (isPrecompile(recipientAddress)) {
      return gas;
    }

    // TODO: Recheck only here to pass tests - after talking with Geth team this will be dropped in
    // future devnets. If there's no value transferred, there's no point in touching the beneficiary
    if (!recipientAddress.equals(originatorAddress)) {
      gas =
          clampedAdd(
              gas,
              frame
                  .getAccessWitness()
                  .touchAddressAndChargeRead(
                      recipientAddress, BASIC_DATA_LEAF_KEY, frame.getRemainingGas()));
    }

    return gas;
  }

  @Override
  public long sloadOperationGasCost(
      final MessageFrame frame, final UInt256 key, final boolean slotIsWarm) {
    long gas =
        frame
            .getAccessWitness()
            .touchAndChargeStorageLoad(frame.getContractAddress(), key, frame.getRemainingGas());

    if (gas == 0) {
      return getWarmStorageReadCost();
    }

    return gas;
  }

  @Override
  public long calculateStorageCost(
      final MessageFrame frame,
      final UInt256 key,
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    long gas =
        frame
            .getAccessWitness()
            .touchAndChargeStorageStore(
                frame.getRecipientAddress(), key, originalValue != null, frame.getRemainingGas());

    if (gas == 0) {
      return SLOAD_GAS;
    }

    return gas;
  }

  @Override
  public long completedCreateContractGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAndChargeContractCreateCompleted(frame.getContractAddress(), frame.getRemainingGas());
  }

  @Override
  public AccessWitness newAccessWitness() {
    return new Eip4762AccessWitness();
  }
}
