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
 * <UL>
 *   <LI>Gas costs for EIP-7702 (Code Delegation)
 * </UL>
 */
public class PragueGasCalculator extends CancunGasCalculator {
  final long existingAccountGasRefund;

  /**
   * The default mainnet target blobs per block for Prague getBlobGasPerBlob() * 6 blobs = 131072 *
   * 6 = 786432 = 0xC0000
   */
  private static final int DEFAULT_TARGET_BLOBS_PER_BLOCK_PRAGUE = 6;

  /** Instantiates a new Prague Gas Calculator. */
  public PragueGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19], DEFAULT_TARGET_BLOBS_PER_BLOCK_PRAGUE);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param targetBlobsPerBlock the target blobs per block
   */
  public PragueGasCalculator(final int targetBlobsPerBlock) {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19], targetBlobsPerBlock);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param maxPrecompile the max precompile
   * @param targetBlobsPerBlock the target blobs per block
   */
  protected PragueGasCalculator(final int maxPrecompile, final int targetBlobsPerBlock) {
    super(maxPrecompile, targetBlobsPerBlock);
    this.existingAccountGasRefund = newAccountGasCost() - CodeDelegation.PER_AUTH_BASE_COST;
  }

  @Override
  public long delegateCodeGasCost(final int delegateCodeListLength) {
    return newAccountGasCost() * delegateCodeListLength;
  }

  @Override
  public long calculateDelegateCodeGasRefund(final long alreadyExistingAccounts) {
    return existingAccountGasRefund * alreadyExistingAccounts;
  }
}
