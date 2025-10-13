/*
 * Copyright 2020 ConsenSys AG.
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
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;

public class MainnetProtocolSpecFactory {

  private final Optional<BigInteger> chainId;
  private final boolean isRevertReasonEnabled;
  private final OptionalLong ecip1017EraRounds;
  private final EvmConfiguration evmConfiguration;
  private final MiningConfiguration miningConfiguration;
  private final boolean isParallelTxProcessingEnabled;
  private final MetricsSystem metricsSystem;

  public MainnetProtocolSpecFactory(
      final Optional<BigInteger> chainId,
      final boolean isRevertReasonEnabled,
      final OptionalLong ecip1017EraRounds,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    this.chainId = chainId;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.ecip1017EraRounds = ecip1017EraRounds;
    this.evmConfiguration = evmConfiguration;
    this.miningConfiguration = miningConfiguration;
    this.isParallelTxProcessingEnabled = isParallelTxProcessingEnabled;
    this.metricsSystem = metricsSystem;
  }

  public ProtocolSpecBuilder frontierDefinition() {
    return MainnetProtocolSpecs.frontierDefinition(
        evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder homesteadDefinition() {
    return MainnetProtocolSpecs.homesteadDefinition(
        evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder daoRecoveryInitDefinition() {
    return MainnetProtocolSpecs.daoRecoveryInitDefinition(
        evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder daoRecoveryTransitionDefinition() {
    return MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
        evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder tangerineWhistleDefinition() {
    return MainnetProtocolSpecs.tangerineWhistleDefinition(
        evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder spuriousDragonDefinition() {
    return MainnetProtocolSpecs.spuriousDragonDefinition(
        chainId, evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder byzantiumDefinition() {
    return MainnetProtocolSpecs.byzantiumDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder constantinopleDefinition() {
    return MainnetProtocolSpecs.constantinopleDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder petersburgDefinition() {
    return MainnetProtocolSpecs.petersburgDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder istanbulDefinition() {
    return MainnetProtocolSpecs.istanbulDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder muirGlacierDefinition() {
    return MainnetProtocolSpecs.muirGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder berlinDefinition() {
    return MainnetProtocolSpecs.berlinDefinition(
        chainId,
        isRevertReasonEnabled,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder londonDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.londonDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder arrowGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.arrowGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder grayGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.grayGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder parisDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.parisDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder shanghaiDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.shanghaiDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder cancunDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.cancunDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder cancunEOFDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.cancunEOFDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder pragueDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.pragueDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder osakaDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.osakaDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  /**
   * The "future" fork consists of EIPs that have been approved for Ethereum Mainnet but not
   * scheduled for a fork. This is also known as "Eligible For Inclusion" (EFI) or "Considered for
   * Inclusion" (CFI).
   *
   * <p>There is no guarantee of the contents of this fork across Besu releases and should be
   * considered unstable.
   *
   * @param genesisConfigOptions the chain options from the genesis config
   * @return a protocol spec for the "Future" fork.
   */
  public ProtocolSpecBuilder futureEipsDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.futureEipsDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  /**
   * The "experimental" fork consists of EIPs and other changes that have not been approved for any
   * fork but are implemented in Besu, either for demonstration or experimentation.
   *
   * <p>There is no guarantee of the contents of this fork across Besu releases and should be
   * considered unstable.
   *
   * @param genesisConfigOptions the chain options from the genesis config
   * @return a protocol spec for the "Experimental" fork.
   */
  public ProtocolSpecBuilder experimentalEipsDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.experimentalEipsDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Classic Protocol Specs
  public ProtocolSpecBuilder dieHardDefinition() {
    return ClassicProtocolSpecs.dieHardDefinition(
        chainId, evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder gothamDefinition() {
    return ClassicProtocolSpecs.gothamDefinition(
        chainId, ecip1017EraRounds, evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder defuseDifficultyBombDefinition() {
    return ClassicProtocolSpecs.defuseDifficultyBombDefinition(
        chainId, ecip1017EraRounds, evmConfiguration, isParallelTxProcessingEnabled, metricsSystem);
  }

  public ProtocolSpecBuilder atlantisDefinition() {
    return ClassicProtocolSpecs.atlantisDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder aghartaDefinition() {
    return ClassicProtocolSpecs.aghartaDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder phoenixDefinition() {
    return ClassicProtocolSpecs.phoenixDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder thanosDefinition() {
    return ClassicProtocolSpecs.thanosDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder magnetoDefinition() {
    return ClassicProtocolSpecs.magnetoDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder mystiqueDefinition() {
    return ClassicProtocolSpecs.mystiqueDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }

  public ProtocolSpecBuilder spiralDefinition() {
    return ClassicProtocolSpecs.spiralDefinition(
        chainId,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration,
        isParallelTxProcessingEnabled,
        metricsSystem);
  }
}
