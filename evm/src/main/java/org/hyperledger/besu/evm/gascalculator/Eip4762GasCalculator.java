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
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.BALANCE_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_KECCAK_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_SIZE_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.NONCE_LEAF_KEY;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.VERSION_LEAF_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
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
    return frame.getAccessWitness().touchAndChargeContractCreateInit(address, !value.isZero());
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

    long gasCost =
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
            true);
    if (super.isPrecompile(to)) {
      return gasCost;
    } else {
      long statelessGas;
      if (frame.getWorldUpdater().get(to) == null) {
        statelessGas = frame.getAccessWitness().touchAndChargeProofOfAbsence(to);
      } else {
        statelessGas = frame.getAccessWitness().touchAndChargeMessageCall(to);
      }
      if (!transferValue.isZero()) {
        statelessGas =
            clampedAdd(
                statelessGas,
                frame.getAccessWitness().touchAndChargeValueTransfer(recipient.getAddress(), to));
      }
      if (statelessGas == 0) {
        return getWarmStorageReadCost();
      }
      return clampedAdd(gasCost, statelessGas);
    }
  }

  @Override
  public long txCreateCost() {
    return CREATE_OPERATION_GAS_COST;
  }

  @Override
  public long codeDepositGasCost(final MessageFrame frame, final int codeSize) {
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
            .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, VERSION_LEAF_KEY);
    statelessGas =
        clampedAdd(
            statelessGas,
            frame
                .getAccessWitness()
                .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, CODE_SIZE_LEAF_KEY));
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
      if (readSize == 1) {
        if ((codeOffset % 31 == 0)) {
          gasCost =
              clampedAdd(
                  gasCost,
                  frame
                      .getAccessWitness()
                      .touchCodeChunks(
                          frame.getContractAddress(), codeOffset + 1, readSize, codeSize));
        }
      } else {
        gasCost =
            clampedAdd(
                gasCost,
                frame
                    .getAccessWitness()
                    .touchCodeChunks(frame.getContractAddress(), codeOffset, readSize, codeSize));
      }
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
              .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, BALANCE_LEAF_KEY);
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
                .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, CODE_KECCAK_LEAF_KEY);
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
                .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, VERSION_LEAF_KEY);
        statelessGas =
            clampedAdd(
                statelessGas,
                frame
                    .getAccessWitness()
                    .touchAddressOnReadAndComputeGas(address, UInt256.ZERO, CODE_SIZE_LEAF_KEY));
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
              .touchAddressOnReadAndComputeGas(originatorAddress, UInt256.ZERO, BALANCE_LEAF_KEY);
      if (!originatorAddress.equals(recipientAddress)) {
        statelessGas =
            clampedAdd(
                statelessGas,
                frame
                    .getAccessWitness()
                    .touchAddressOnReadAndComputeGas(
                        recipientAddress, UInt256.ZERO, BALANCE_LEAF_KEY));
      }
      if (!inheritance.isZero()) {
        statelessGas =
            clampedAdd(
                statelessGas,
                frame
                    .getAccessWitness()
                    .touchAddressOnWriteAndComputeGas(
                        originatorAddress, UInt256.ZERO, BALANCE_LEAF_KEY));
        if (!originatorAddress.equals(recipientAddress)) {
          statelessGas =
              clampedAdd(
                  statelessGas,
                  frame
                      .getAccessWitness()
                      .touchAddressOnWriteAndComputeGas(
                          recipientAddress, UInt256.ZERO, BALANCE_LEAF_KEY));
        }
        if (recipient == null) {
          statelessGas =
              clampedAdd(
                  statelessGas,
                  frame
                      .getAccessWitness()
                      .touchAddressOnWriteAndComputeGas(
                          recipientAddress, UInt256.ZERO, VERSION_LEAF_KEY));
          statelessGas =
              clampedAdd(
                  statelessGas,
                  frame
                      .getAccessWitness()
                      .touchAddressOnWriteAndComputeGas(
                          recipientAddress, UInt256.ZERO, NONCE_LEAF_KEY));
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
