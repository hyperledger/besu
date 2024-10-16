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
public class OsakaGasCalculator extends PragueGasCalculator {

  static final long MIN_RETAINED_GAS = 5_000;
  static final long MIN_CALLEE_GAS = 2300;

  /** Instantiates a new Prague Gas Calculator. */
  public OsakaGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  protected OsakaGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  @Override
  public long getMinRetainedGas() {
    return MIN_RETAINED_GAS;
  }

  @Override
  public long getMinCalleeGas() {
    return MIN_CALLEE_GAS;
  }
}
