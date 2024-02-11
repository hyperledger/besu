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
 *
 */

package org.hyperledger.besu.ethereum.api;

import java.util.function.LongSupplier;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
public abstract class ApiConfiguration {

  public static final long DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT = 0L;
  public static final long DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT = Long.MAX_VALUE;

  @Value.Default
  public long getGasPriceBlocks() {
    return 100;
  }

  @Value.Default
  public double getGasPricePercentile() {
    return 50.0d;
  }

  @Value.Default
  @Value.Auxiliary
  public LongSupplier getGasPriceMinSupplier() {
    return () -> 1_000_000_000L; // 1 GWei
  }

  @Value.Default
  public long getGasPriceMax() {
    return 500_000_000_000L; // 500 GWei
  }

  @Value.Derived
  public double getGasPriceFraction() {
    return getGasPricePercentile() / 100.0;
  }

  @Value.Default
  public Long getMaxLogsRange() {
    return 5000L;
  }

  @Value.Default
  public Long getGasCap() {
    return 0L;
  }

  @Value.Default
  public boolean isGasAndPriorityFeeLimitingEnabled() {
    return false;
  }

  @Value.Default
  public Long getLowerBoundGasAndPriorityFeeCoefficient() {
    return DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;
  }

  @Value.Default
  public Long getUpperBoundGasAndPriorityFeeCoefficient() {
    return DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;
  }

  @Value.Default
  public Long getMaxTraceFilterRange() {
    return 1000L;
  }
}
