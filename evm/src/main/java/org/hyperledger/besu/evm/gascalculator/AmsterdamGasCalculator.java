/*
 * Copyright contributors to Besu.
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

/**
 * Gas Calculator for Amsterdam
 *
 * <UL>
 *   <LI>EIP-7928: gas cost per item for block access list size limit
 * </UL>
 */
public class AmsterdamGasCalculator extends OsakaGasCalculator {

  /**
   * EIP-7928: gas cost per item for block access list size limit (bal_items <= block_gas_limit /
   * ITEM_COST).
   */
  private static final long BLOCK_ACCESS_LIST_ITEM_COST = 2000L;

  /** Instantiates a new Amsterdam Gas Calculator. */
  public AmsterdamGasCalculator() {}

  /**
   * Instantiates a new Amsterdam Gas Calculator
   *
   * @param maxPrecompile the max precompile address from the L1 precompile range (0x01 - 0xFF)
   * @param maxL2Precompile max precompile address from the L2 precompile space (0x0100 - 0x01FF)
   */
  public AmsterdamGasCalculator(final int maxPrecompile, final int maxL2Precompile) {
    super(maxPrecompile, maxL2Precompile);
  }

  /**
   * Instantiates a new Amsterdam Gas Calculator, uses default P256_VERIFY as max L2 precompile.
   *
   * @param maxPrecompile the max precompile address from the L1 precompile range (0x01 - 0xFF)
   */
  public AmsterdamGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  @Override
  public long getBlockAccessListItemCost() {
    return BLOCK_ACCESS_LIST_ITEM_COST;
  }
}
