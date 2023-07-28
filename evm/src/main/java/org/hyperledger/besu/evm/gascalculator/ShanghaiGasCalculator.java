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

import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

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
  public long createOperationGasCost(final MessageFrame frame) {
    final long initCodeLength = clampedToLong(frame.getStackItem(2));
    return clampedAdd(super.createOperationGasCost(frame), calculateInitGasCost(initCodeLength));
  }

  private static long calculateInitGasCost(final long initCodeLength) {
    final int dataLength = (int) Math.ceil(initCodeLength / 32.0);
    return dataLength * INIT_CODE_COST;
  }
}
