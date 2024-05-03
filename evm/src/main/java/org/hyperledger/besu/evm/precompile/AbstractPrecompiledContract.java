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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Skeleton class for {@link PrecompiledContract} implementations. */
@SuppressWarnings("unused")
public abstract class AbstractPrecompiledContract implements PrecompiledContract {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractPrecompiledContract.class);

  private final GasCalculator gasCalculator;

  private final String name;

  /**
   * Instantiates a new Abstract precompiled contract.
   *
   * @param name the name
   * @param gasCalculator the gas calculator
   */
  protected AbstractPrecompiledContract(final String name, final GasCalculator gasCalculator) {
    this.name = name;
    this.gasCalculator = gasCalculator;
  }

  /**
   * Gas calculator.
   *
   * @return the gas calculator
   */
  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public String getName() {
    return name;
  }
}
