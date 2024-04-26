/*
 * Copyright Hyperledger Besu contributors.
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
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Gas Calculator for Prague
 *
 * <p>Placeholder for new gas schedule items. If Prague finalzies without changes this can be
 * removed
 *
 * <UL>
 *   <LI>TBD
 * </UL>
 */
public class PragueGasCalculator extends ShanghaiGasCalculator {
  private static final long CREATE_OPERATION_GAS_COST = 1_000L;

  /** Instantiates a new Prague Gas Calculator. */
  public PragueGasCalculator() {
    this(KZG_POINT_EVAL.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  protected PragueGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
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

    long cost =
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
    if (!super.isPrecompile(to)) {
      if (frame.getWorldUpdater().get(to) == null) {
        cost = clampedAdd(cost, frame.getAccessWitness().touchAndChargeProofOfAbsence(to));
      } else {
        cost = clampedAdd(cost, frame.getAccessWitness().touchAndChargeMessageCall(to));
      }
      if (!transferValue.isZero()) {
        cost =
            clampedAdd(
                cost,
                frame.getAccessWitness().touchAndChargeValueTransfer(recipient.getAddress(), to));
      }
    }
    return cost;
  }

  @Override
  public long txCreateCost() {
    return CREATE_OPERATION_GAS_COST;
  }
}
