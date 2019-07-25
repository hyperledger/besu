/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.difficulty.fixed.FixedDifficultyCalculators;
import tech.pegasys.pantheon.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;

import java.math.BigInteger;
import java.time.Clock;
import java.util.function.Function;

/** Provides {@link ProtocolSpec} lookups for mainnet hard forks. */
public class MainnetProtocolSchedule {

  public static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  public static ProtocolSchedule<Void> create(final Clock clock) {
    return fromConfig(
        GenesisConfigFile.mainnet().getConfigOptions(), PrivacyParameters.DEFAULT, false, clock);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param privacyParameters the parameters set for private transactions
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param clock System clock
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule<Void> fromConfig(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final Clock clock) {
    if (FixedDifficultyCalculators.isFixedDifficultyInConfig(config)) {
      return FixedDifficultyProtocolSchedule.create(
          config, privacyParameters, isRevertReasonEnabled, clock);
    }
    return new ProtocolScheduleBuilder<Void>(
            config,
            DEFAULT_CHAIN_ID,
            Function.identity(),
            privacyParameters,
            isRevertReasonEnabled,
            clock)
        .createProtocolSchedule();
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param clock System clock
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule<Void> fromConfig(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled, final Clock clock) {
    return fromConfig(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled, clock);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param clock System clock
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule<Void> fromConfig(
      final GenesisConfigOptions config, final Clock clock) {
    return fromConfig(config, PrivacyParameters.DEFAULT, false, clock);
  }
}
