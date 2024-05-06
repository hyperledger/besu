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

import org.hyperledger.besu.datatypes.Address;
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
public class PragueGasCalculator extends CancunGasCalculator {
  private final int AUTH_OP_FIXED_FEE = 3100;

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
  public long authOperationGasCost(
      final MessageFrame frame, final long offset, final long length, final Address authority) {
    final long memoryExpansionGasCost = memoryExpansionGasCost(frame, offset, length);
    final long accessFee = frame.isAddressWarm(authority) ? 100 : 2600;
    final long gasCost = AUTH_OP_FIXED_FEE + memoryExpansionGasCost + accessFee;
    return gasCost;
  }
}
