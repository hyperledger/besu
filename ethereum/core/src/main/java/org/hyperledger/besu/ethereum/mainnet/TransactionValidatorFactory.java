/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.PermissionTransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Suppliers;

public class TransactionValidatorFactory {
  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final FeeMarket feeMarket;
  private final boolean disallowSignatureMalleability;
  private final Optional<BigInteger> chainId;
  private final Set<TransactionType> acceptedTransactionTypes;
  private final int maxInitcodeSize;
  private Optional<PermissionTransactionFilter> permissionTransactionFilter = Optional.empty();

  public TransactionValidatorFactory(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId) {
    this(
        gasCalculator,
        gasLimitCalculator,
        checkSignatureMalleability,
        chainId,
        Set.of(TransactionType.FRONTIER));
  }

  public TransactionValidatorFactory(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes) {
    this(
        gasCalculator,
        gasLimitCalculator,
        FeeMarket.legacy(),
        checkSignatureMalleability,
        chainId,
        acceptedTransactionTypes,
        Integer.MAX_VALUE);
  }

  public TransactionValidatorFactory(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final int maxInitcodeSize) {
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.feeMarket = feeMarket;
    this.disallowSignatureMalleability = checkSignatureMalleability;
    this.chainId = chainId;
    this.acceptedTransactionTypes = acceptedTransactionTypes;
    this.maxInitcodeSize = maxInitcodeSize;
  }

  public void setPermissionTransactionFilter(
      final PermissionTransactionFilter permissionTransactionFilter) {
    this.permissionTransactionFilter = Optional.of(permissionTransactionFilter);
  }

  public TransactionValidator get() {
    return Suppliers.memoize(this::createTransactionValidator).get();
  }

  private TransactionValidator createTransactionValidator() {
    final TransactionValidator baseValidator =
        new MainnetTransactionValidator(
            gasCalculator,
            gasLimitCalculator,
            feeMarket,
            disallowSignatureMalleability,
            chainId,
            acceptedTransactionTypes,
            maxInitcodeSize);
    if (permissionTransactionFilter.isPresent()) {
      return new PermissionTransactionValidator(baseValidator, permissionTransactionFilter.get());
    }
    return baseValidator;
  }
}
