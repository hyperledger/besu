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
 * Gas Calculator for Osaka
 *
 * <UL>
 *   <LI>TBD
 * </UL>
 */
public class OsakaGasCalculator extends PragueGasCalculator {

  /** Instantiates a new Osaka Gas Calculator. */
  public OsakaGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Osaka Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  protected OsakaGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }
}
