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

import static org.hyperledger.besu.evm.internal.Words.numWords;

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
  protected long contractCreationCost(final int initCodeLength) {
    return txCreateExtraGasCost() + initcodeCost(initCodeLength);
  }

  @Override
  public long initcodeCost(final int initCodeLength) {
    return numWords(initCodeLength) * INIT_CODE_COST;
  }
}
