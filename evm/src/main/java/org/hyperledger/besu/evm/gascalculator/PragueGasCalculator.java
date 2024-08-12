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

import org.hyperledger.besu.datatypes.CodeDelegation;

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
  static final long EXISTING_ACCOUNT_GAS_REFUND =
      CodeDelegation.PER_EMPTY_ACCOUNT_COST - CodeDelegation.PER_AUTH_BASE_COST;

  /** Instantiates a new Prague Gas Calculator. */
  public PragueGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19]);
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
  public long delegateCodeGasCost(final int delegateCodeListLength) {
    return CodeDelegation.PER_EMPTY_ACCOUNT_COST * delegateCodeListLength;
  }

  @Override
  public long calculateDelegateCodeGasRefund(final int alreadyExistingAccountSize) {
    return EXISTING_ACCOUNT_GAS_REFUND * alreadyExistingAccountSize;
  }

  @Override
  public long delegatedCodeResolutionGasCost(final boolean isWarm) {
    return isWarm ? getWarmStorageReadCost() : getColdAccountAccessCost();
  }
}
