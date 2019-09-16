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
package org.hyperledger.besu.ethereum.difficulty.fixed;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;

/** A ProtocolSchedule which behaves similarly to MainNet, but with a much reduced difficulty. */
public class FixedDifficultyProtocolSchedule {

  public static ProtocolSchedule<Void> create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {
    return new ProtocolScheduleBuilder<>(
            config,
            builder -> builder.difficultyCalculator(FixedDifficultyCalculators.calculator(config)),
            privacyParameters,
            isRevertReasonEnabled)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule<Void> create(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  public static ProtocolSchedule<Void> create(final GenesisConfigOptions config) {
    return create(config, PrivacyParameters.DEFAULT, false);
  }
}
