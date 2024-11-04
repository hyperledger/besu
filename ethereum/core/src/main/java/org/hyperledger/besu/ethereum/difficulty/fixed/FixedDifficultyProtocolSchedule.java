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
package org.hyperledger.besu.ethereum.difficulty.fixed;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;

/**
 * A ProtocolSchedule which behaves similarly to pre-merge MainNet, but with a much reduced
 * difficulty.
 */
public class FixedDifficultyProtocolSchedule {

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return new ProtocolScheduleBuilder(
            config,
            Optional.empty(),
            ProtocolSpecAdapters.create(
                0,
                builder ->
                    builder.difficultyCalculator(FixedDifficultyCalculators.calculator(config))),
            privacyParameters,
            isRevertReasonEnabled,
            evmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return create(
        config,
        PrivacyParameters.DEFAULT,
        isRevertReasonEnabled,
        evmConfiguration,
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return create(
        config,
        PrivacyParameters.DEFAULT,
        false,
        evmConfiguration,
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }
}
