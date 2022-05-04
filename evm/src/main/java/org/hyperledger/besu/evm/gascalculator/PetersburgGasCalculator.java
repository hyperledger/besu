/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.evm.account.Account;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * Gas Calculator for Petersberg Hard Fork. Rollback EIP-1283.
 *
 * <p>Neither {@link TangerineWhistleGasCalculator} nor {@link SpuriousDragonGasCalculator} overrode
 * these two methods so {@link FrontierGasCalculator} is the source.
 */
public class PetersburgGasCalculator extends ConstantinopleGasCalculator {

  /** Same as {#link {@link FrontierGasCalculator#STORAGE_SET_GAS_COST} */
  private static final long STORAGE_SET_GAS_COST = 20_000L;
  /** Same as {#link {@link FrontierGasCalculator#STORAGE_RESET_GAS_COST} */
  private static final long STORAGE_RESET_GAS_COST = 5_000L;
  /** Same as {#link {@link FrontierGasCalculator#STORAGE_RESET_REFUND_AMOUNT} */
  private static final long STORAGE_RESET_REFUND_AMOUNT = 15_000L;

  /**
   * Same as {#link {@link FrontierGasCalculator#calculateStorageCost(Account, UInt256, UInt256)}
   */
  @Override
  public long calculateStorageCost(
      final Account account, final UInt256 key, final UInt256 newValue) {
    return !newValue.isZero() && account.getStorageValue(key).isZero()
        ? STORAGE_SET_GAS_COST
        : STORAGE_RESET_GAS_COST;
  }

  /**
   * Same as {#link {@link FrontierGasCalculator#calculateStorageRefundAmount(Account, UInt256,
   * UInt256)}
   */
  @Override
  public long calculateStorageRefundAmount(
      final Account account, final UInt256 key, final UInt256 newValue) {
    return newValue.isZero() && !account.getStorageValue(key).isZero()
        ? STORAGE_RESET_REFUND_AMOUNT
        : 0L;
  }
}
