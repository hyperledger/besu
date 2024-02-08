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
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

import java.util.function.Supplier;

/** The Shanghai gas calculator. */
public class ShanghaiGasCalculator extends LondonGasCalculator {

  private static final long INIT_CODE_COST = 2L;
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
  public long createOperationGasCost(final MessageFrame frame) {
    final long initCodeLength = clampedToLong(frame.getStackItem(2));
    long cost = super.createOperationGasCost(frame);
    cost = clampedAdd(cost, frame.getAccessWitness().touchAndChargeContractCreateInit(frame.getRecipientAddress(),!frame.getStackItem(0).isZero()));
    //TODO VERKLE maybe we should onlu change this upon the completion of the contract creation, geth does at the end of the execution
    cost = clampedAdd(cost, frame.getAccessWitness().touchAndChargeContractCreateCompleted(frame.getRecipientAddress()));
    return clampedAdd(cost, calculateInitGasCost(initCodeLength));
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
    if(frame.getWorldUpdater().get(to) == null){
      cost = clampedAdd(baseCost,frame.getAccessWitness().touchAndChargeProofOfAbsence(to));
    }
    else{
      if(!super.isPrecompile(to)){
        cost = clampedAdd(baseCost,frame.getAccessWitness().touchAndChargeMessageCall(to));
      }
    }

    if(!transferValue.isZero()){
      cost = clampedAdd(baseCost,frame.getAccessWitness().touchAndChargeValueTransfer(recipient.getAddress(),to));
    }
    return cost;
  }


  @Override
  public long calculateStorageCost(
          final MessageFrame frame, final UInt256 newValue, final Supplier<UInt256> currentValue, final Supplier<UInt256> originalValue){

    long gasCost = super.calculateStorageCost(frame, newValue, currentValue, originalValue);
    //TODO VEKLE: right now we're not computing what is the tree index and subindex we're just charging the cost of writing to the storage
    if(!newValue.equals(currentValue.get())){
      gasCost += frame.getAccessWitness().touchAddressOnWriteAndComputeGas(frame.getRecipientAddress(),0,64);
    }

    return gasCost;
  }
}
