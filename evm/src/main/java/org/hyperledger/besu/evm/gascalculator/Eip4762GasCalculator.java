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

import static org.hyperledger.besu.datatypes.Address.KZG_POINT_EVAL;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.BASIC_DATA_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_HASH_LEAF_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.stateless.Eip4762AccessWitness;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.units.bigints.UInt256;

public class Eip4762GasCalculator extends PragueGasCalculator {
  private static final long CREATE_OPERATION_GAS_COST = 1_000L;

  /** Instantiates a new Prague Gas Calculator. */
  public Eip4762GasCalculator() {
    super(KZG_POINT_EVAL.toArrayUnsafe()[19]);
  }

  @Override
  public long getColdSloadCost() {
    return 0; // no cold gas cost after verkle
  }

  @Override
  public long getColdAccountAccessCost() {
    return 0; // no cold gas cost after verkle
  }

  @Override
  public long initcodeCost(final int initCodeLength) {
    return super.initcodeCost(initCodeLength);
  }

  @Override
  public long initcodeStatelessCost(
      final MessageFrame frame, final Address address, final Wei value) {
    return frame.getAccessWitness().touchAndChargeContractCreateInit(address);
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
                      frame.getContractAddress(), to, !frame.accountExists(to)));
    }

    if (super.isPrecompile(to)) { // Also if !isSystemContract but that's not implemented yet
      return clampedAdd(gas, accountIsWarm ? getWarmStorageReadCost() : 0);
    }

    long messageCallGas = frame.getAccessWitness().touchAndChargeMessageCall(to);
    if (messageCallGas == 0) {
      messageCallGas = getWarmStorageReadCost();
    }
    gas = clampedAdd(gas, messageCallGas);

    return gas;
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
    // Check the remaining gas costs here, should be 0 until now
    return frame
        .getAccessWitness()
        .touchCodeChunksUponContractCreation(frame.getContractAddress(), codeSize);
  }

  @Override
  public long calculateStorageCost(
      final MessageFrame frame,
      final UInt256 key,
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    long gasCost = 0;
    final UInt256 localCurrentValue = currentValue.get();
    if (localCurrentValue.equals(newValue) || !localCurrentValue.equals(originalValue.get())) {
      gasCost += SLOAD_GAS;
    }

    // Check the remaining gas calculations as they should be 0 from here on

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
            .touchAddressOnWriteResetAndComputeGas(
                frame.getRecipientAddress(), treeIndexes.get(0), treeIndexes.get(1));

    if (gasCost == 0) {
      return getWarmStorageReadCost();
    }

    return gasCost;
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
    long gasCost = extCodeCopyOperationGasCost(frame, memOffset, readSize);

    long statelessGas =
        frame
            .getAccessWitness()
            .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, BASIC_DATA_LEAF_KEY);
    if (statelessGas == 0) {
      statelessGas = getWarmStorageReadCost();
    }

    if (!frame.wasCreatedInTransaction(frame.getContractAddress())) {
      statelessGas =
          clampedAdd(
              statelessGas,
              frame.getAccessWitness().touchCodeChunks(address, codeOffset, readSize, codeSize));
    }

    return clampedAdd(gasCost, statelessGas);
  }

  @Override
  public long codeCopyOperationGasCost(
      final MessageFrame frame,
      final long memOffset,
      final long codeOffset,
      final long readSize,
      final long codeSize) {
    long gasCost = super.dataCopyOperationGasCost(frame, memOffset, readSize);
    if (!frame.wasCreatedInTransaction(frame.getContractAddress())) {
      gasCost =
          clampedAdd(
              gasCost,
              frame
                  .getAccessWitness()
                  .touchCodeChunks(frame.getContractAddress(), codeOffset, readSize, codeSize));
    }
    return gasCost;
  }

  @Override
  public long pushOperationGasCost(
      final MessageFrame frame, final long codeOffset, final long readSize, final long codeSize) {
    long gasCost = super.pushOperationGasCost(frame, codeOffset, readSize, codeSize);
    if (!frame.wasCreatedInTransaction(frame.getContractAddress())) {
      gasCost =
          clampedAdd(
              gasCost,
              frame
                  .getAccessWitness()
                  .touchCodeChunks(frame.getContractAddress(), codeOffset, readSize, codeSize));
    }
    return gasCost;
  }

  @Override
  public long getBalanceOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Optional<Address> maybeAddress) {
    if (maybeAddress.isPresent()) {
      final Address address = maybeAddress.get();
      final long statelessGas =
          frame
              .getAccessWitness()
              .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, BASIC_DATA_LEAF_KEY);
      if (statelessGas == 0) {
        return getWarmStorageReadCost();
      } else {
        return statelessGas;
      }
    }
    return 0L;
  }

  @Override
  public long extCodeHashOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Optional<Address> maybeAddress) {
    if (maybeAddress.isPresent()) {
      final Address address = maybeAddress.get();
      if (isPrecompile(address)) {
        return 0L;
      } else {
        final long statelessGas =
            frame
                .getAccessWitness()
                .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, CODE_HASH_LEAF_KEY);
        if (statelessGas == 0) {
          return getWarmStorageReadCost();
        } else {
          return statelessGas;
        }
      }
    }
    return 0L;
  }

  @Override
  public long getExtCodeSizeOperationGasCost(
      final MessageFrame frame, final boolean accountIsWarm, final Optional<Address> maybeAddress) {

    if (maybeAddress.isPresent()) {
      final Address address = maybeAddress.get();
      if (isPrecompile(address)) {
        return 0L;
      } else {
        long statelessGas =
            frame
                .getAccessWitness()
                .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, BASIC_DATA_LEAF_KEY);
        if (statelessGas == 0) {
          return getWarmStorageReadCost();
        } else {
          return statelessGas;
        }
      }
    }
    return 0L;
  }

  @Override
  public long selfDestructOperationGasCost(
      final MessageFrame frame,
      final Account recipient,
      final Address recipientAddress,
      final Wei inheritance,
      final Address originatorAddress) {
    final long gasCost =
        super.selfDestructOperationGasCost(
            frame, recipient, recipientAddress, inheritance, originatorAddress);
    if (isPrecompile(recipientAddress)) {
      return gasCost;
    } else {
      long statelessGas =
          frame
              .getAccessWitness()
              .touchAddressOnReadAndComputeGas(
                  originatorAddress, UInt256.ZERO, BASIC_DATA_LEAF_KEY);
      if (!originatorAddress.equals(recipientAddress)) {
        statelessGas =
            clampedAdd(
                statelessGas,
                frame
                    .getAccessWitness()
                    .touchAddressOnReadAndComputeGas(
                        recipientAddress, UInt256.ZERO, BASIC_DATA_LEAF_KEY));
      }
      if (!inheritance.isZero()) {
        statelessGas =
            clampedAdd(
                statelessGas,
                frame
                    .getAccessWitness()
                    .touchAddressOnWriteResetAndComputeGas(
                        originatorAddress, UInt256.ZERO, BASIC_DATA_LEAF_KEY));
        if (!originatorAddress.equals(recipientAddress)) {
          statelessGas =
              clampedAdd(
                  statelessGas,
                  frame
                      .getAccessWitness()
                      .touchAddressOnWriteResetAndComputeGas(
                          recipientAddress, UInt256.ZERO, BASIC_DATA_LEAF_KEY));
        } else if (recipient == null) {
          statelessGas =
              clampedAdd(
                  statelessGas,
                  frame
                      .getAccessWitness()
                      .touchAddressOnWriteResetAndComputeGas(
                          recipientAddress, UInt256.ZERO, BASIC_DATA_LEAF_KEY));
        }
      }
      return clampedAdd(gasCost, statelessGas);
    }
  }

  @Override
  public long getSloadOperationGasCost(
      final MessageFrame frame, final UInt256 key, final boolean slotIsWarm) {
    AccessWitness accessWitness = frame.getAccessWitness();
    List<UInt256> treeIndexes = accessWitness.getStorageSlotTreeIndexes(key);
    long gasCost =
        frame
            .getAccessWitness()
            .touchAddressOnReadAndComputeGas(
                frame.getContractAddress(), treeIndexes.get(0), treeIndexes.get(1));
    if (gasCost == 0) {
      return getWarmStorageReadCost();
    }
    return gasCost;
  }

  @Override
  public long computeBaseAccessEventsCost(
      final AccessWitness accessWitness, final Transaction transaction) {

    System.out.println(">>>>>>>>>>> NOT CHARGED");
    accessWitness.touchTxOriginAndComputeGas(transaction.getSender());
    if (!isContractCreation(transaction)) {
      @SuppressWarnings("OptionalGetWithoutIsPresent") // isContractCreation tests isPresent
      final Address to = transaction.getTo().get();
      final boolean sendsValue = !transaction.getValue().equals(Wei.ZERO);
      accessWitness.touchTxExistingAndComputeGas(to, sendsValue);
    }
    System.out.println(">>>>>>>>>>> NOT CHARGED");
    // Not charged
    return 0;
  }

  private static boolean isContractCreation(final Transaction transaction) {
    return transaction.getTo().isEmpty();
  }

  @Override
  public long completedCreateContractGasCost(final MessageFrame frame) {
    return frame
        .getAccessWitness()
        .touchAndChargeContractCreateCompleted(frame.getContractAddress());
  }

  @Override
  public AccessWitness newAccessWitness() {
    return new Eip4762AccessWitness();
  }
}
