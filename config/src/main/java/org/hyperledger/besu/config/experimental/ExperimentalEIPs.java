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
 * Flags defined in this class must be used with caution, and strictly reserved to experimental
 * EIPs.
 */
public class ExperimentalEIPs {
  // To make it easier for tests to reset the value to default
  public static final long EIP1559_BASEFEE_DEFAULT_VALUE = 1000000000L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-basefee-max-change-denominator"},
      arity = "1")
  public static Long basefeeMaxChangeDenominator = 8L;

  @Option(
      hidden = true,
      names = {"--Xeip1559-initial-base-fee"},
      arity = "1")
  public static Long initialBasefee = EIP1559_BASEFEE_DEFAULT_VALUE;

  @Option(
      hidden = true,
      names = {"--Xeip1559-slack-coefficient"},
      arity = "1")
  public static Long slackCoefficient = 2L;
}
