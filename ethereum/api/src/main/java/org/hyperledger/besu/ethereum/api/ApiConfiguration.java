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
package org.hyperledger.besu.ethereum.api;

import java.util.function.LongSupplier;

import org.immutables.value.Value;

/** The type Api configuration. */
@Value.Immutable
@Value.Style(allParameters = true)
public abstract class ApiConfiguration {

  /** The constant DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT. */
  public static final long DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT = 0L;

  /** The constant DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT. */
  public static final long DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT = Long.MAX_VALUE;

  /** Default Constructor */
  protected ApiConfiguration() {}

  /**
   * Gets gas price blocks.
   *
   * @return the gas price blocks
   */
  @Value.Default
  public long getGasPriceBlocks() {
    return 100;
  }

  /**
   * Gets gas price percentile.
   *
   * @return the gas price percentile
   */
  @Value.Default
  public double getGasPricePercentile() {
    return 50.0d;
  }

  /**
   * Gets gas price min supplier.
   *
   * @return the gas price min supplier
   */
  @Value.Default
  @Value.Auxiliary
  public LongSupplier getGasPriceMinSupplier() {
    return () -> 1_000_000_000L; // 1 GWei
  }

  /**
   * Gets gas price max.
   *
   * @return the gas price max
   */
  @Value.Default
  public long getGasPriceMax() {
    return 500_000_000_000L; // 500 GWei
  }

  /**
   * Gets gas price fraction.
   *
   * @return the gas price fraction
   */
  @Value.Derived
  public double getGasPriceFraction() {
    return getGasPricePercentile() / 100.0;
  }

  /**
   * Gets max logs range.
   *
   * @return the max logs range
   */
  @Value.Default
  public Long getMaxLogsRange() {
    return 5000L;
  }

  /**
   * Gets gas cap.
   *
   * @return the gas cap
   */
  @Value.Default
  public Long getGasCap() {
    return 0L;
  }

  /**
   * Is gas and priority fee limiting enabled boolean.
   *
   * @return the boolean
   */
  @Value.Default
  public boolean isGasAndPriorityFeeLimitingEnabled() {
    return false;
  }

  /**
   * Gets lower bound gas and priority fee coefficient.
   *
   * @return the lower bound gas and priority fee coefficient
   */
  @Value.Default
  public Long getLowerBoundGasAndPriorityFeeCoefficient() {
    return DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;
  }

  /**
   * Gets upper bound gas and priority fee coefficient.
   *
   * @return the upper bound gas and priority fee coefficient
   */
  @Value.Default
  public Long getUpperBoundGasAndPriorityFeeCoefficient() {
    return DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;
  }

  /**
   * Gets max trace filter range.
   *
   * @return the max trace filter range
   */
  @Value.Default
  public Long getMaxTraceFilterRange() {
    return 1000L;
  }
}
