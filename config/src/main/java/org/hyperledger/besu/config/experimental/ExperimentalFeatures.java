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
 * featurres.
 */
public class ExperimentalFeatures {
  public static final int FAUCET_DEFAULT_VALUE = 5;

  @Option(
      hidden = true,
      names = {"--Xfaucet-value"},
      description = "Faucet value denominate in ETH (default: ${DEFAULT-VALUE})",
      arity = "1")
  public static Integer faucetValue = FAUCET_DEFAULT_VALUE;

  @Option(
      hidden = true,
      names = {"--Xfaucet-private-key"},
      description = "Faucet private key (default: ${DEFAULT-VALUE})",
      arity = "1")
  public static String faucetPrivateKey =
      "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
}
