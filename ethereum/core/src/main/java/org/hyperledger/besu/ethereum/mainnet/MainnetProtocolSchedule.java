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
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyCalculators;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
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
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled
   * @param metricsSystem A metricSystem instance to expose metrics in the underlying calls
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final Optional<PrivacyParameters> privacyParameters,
      final Optional<Boolean> isRevertReasonEnabled,
      final Optional<EvmConfiguration> evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    if (FixedDifficultyCalculators.isFixedDifficultyInConfig(config)) {
      return FixedDifficultyProtocolSchedule.create(
          config,
          privacyParameters.orElse(PrivacyParameters.DEFAULT),
          isRevertReasonEnabled.orElse(false),
          evmConfiguration.orElse(EvmConfiguration.DEFAULT),
          miningConfiguration,
          badBlockManager,
          isParallelTxProcessingEnabled,
          metricsSystem);
    }
    return new ProtocolScheduleBuilder(
            config,
            Optional.of(DEFAULT_CHAIN_ID),
            ProtocolSpecAdapters.create(0, Function.identity()),
            privacyParameters.orElse(PrivacyParameters.DEFAULT),
            isRevertReasonEnabled.orElse(false),
            evmConfiguration.orElse(EvmConfiguration.DEFAULT),
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .createProtocolSchedule();
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param evmConfiguration how to configure the EVMs jumpdest cache
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.empty(),
        Optional.of(isRevertReasonEnabled),
        Optional.of(evmConfiguration),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param evmConfiguration size of
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.empty(),
        Optional.empty(),
        Optional.of(evmConfiguration),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }
}
