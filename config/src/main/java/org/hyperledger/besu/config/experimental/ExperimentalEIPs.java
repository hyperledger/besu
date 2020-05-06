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
package org.hyperledger.besu.config.experimental;

import picocli.CommandLine.Option;

/**
 * Flags defined in those class must be used with cautious, and strictly reserved to experimental
 * EIPs.
 */
public class ExperimentalEIPs {
  // To make it easier for tests to reset the value to default
  public static final boolean EIP1559_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xeip1559-enabled"},
      description = "Enable experimental EIP-1559 fee market change (default: ${DEFAULT-VALUE})",
      arity = "1")
  public static boolean eip1559Enabled = EIP1559_ENABLED_DEFAULT_VALUE;

  // To make it easier for tests to reset the value to default
  public static final boolean BERLIN_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xberlin-enabled"},
      description = "Enable non-finalized Berlin features (default: ${DEFAULT-VALUE})",
      arity = "1")
  public static boolean berlinEnabled = BERLIN_ENABLED_DEFAULT_VALUE;

  public static void eip1559MustBeEnabled() {
    if (!eip1559Enabled) {
      throw new RuntimeException("EIP-1559 feature flag must be enabled --Xeip1559-enabled");
    }
  }

  @Option(
      hidden = true,
      names = {"--Xeip1559-basefee-max-change-denominator"},
      arity = "1")
  public static Long basefeeMaxChangeDenominator = 8L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-target-gas-used"},
      arity = "1")
  public static Long targetGasUsed = 10000000L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-slack-coefficient"},
      arity = "1")
  public static Long slackCoefficient = 2L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-decay-range"},
      arity = "1")
  public static Long decayRange = 1000000L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-initial-base-fee"},
      arity = "1")
  public static Long initialBasefee = 1000000000L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-per-tx-gas-limit"},
      arity = "1")
  public static Long perTxGasLimit = 8000000L;
}
