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
package tech.pegasys.pantheon.ethereum.difficulty.fixed;

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolScheduleBuilder;

import java.time.Clock;

/** A ProtocolSchedule which behaves similarly to MainNet, but with a much reduced difficulty. */
public class FixedDifficultyProtocolSchedule {

  public static ProtocolSchedule<Void> create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final Clock clock) {
    return new ProtocolScheduleBuilder<>(
            config,
            builder -> builder.difficultyCalculator(FixedDifficultyCalculators.calculator(config)),
            privacyParameters,
            isRevertReasonEnabled,
            clock)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule<Void> create(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled, final Clock clock) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled, clock);
  }

  public static ProtocolSchedule<Void> create(
      final GenesisConfigOptions config, final Clock clock) {
    return create(config, PrivacyParameters.DEFAULT, false, clock);
  }
}
