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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyCalculators;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.function.Function;

/** Provides {@link ProtocolSpec} lookups for mainnet hard forks. */
public class MainnetProtocolSchedule {

  public static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param privacyParameters the parameters set for private transactions
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param evmConfiguration how to configure the EVMs jumpdest cache
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration) {
    if (FixedDifficultyCalculators.isFixedDifficultyInConfig(config)) {
      return FixedDifficultyProtocolSchedule.create(
          config, privacyParameters, isRevertReasonEnabled, evmConfiguration);
    }
    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            ProtocolSpecAdapters.create(0, Function.identity()),
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum(),
            evmConfiguration)
        .createProtocolSchedule();
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param evmConfiguration how to configure the EVMs jumpdest cache
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration) {
    return fromConfig(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled, evmConfiguration);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param evmConfiguration size of
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config, final EvmConfiguration evmConfiguration) {
    return fromConfig(config, PrivacyParameters.DEFAULT, false, evmConfiguration);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(final GenesisConfigOptions config) {
    return fromConfig(config, PrivacyParameters.DEFAULT, false, EvmConfiguration.DEFAULT);
  }
}
