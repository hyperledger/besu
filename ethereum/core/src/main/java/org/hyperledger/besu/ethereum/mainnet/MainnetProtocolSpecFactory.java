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
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.ethereum.core.MiningParameters;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class MainnetProtocolSpecFactory {

  private final Optional<BigInteger> chainId;
  private final OptionalInt contractSizeLimit;
  private final OptionalInt evmStackSize;
  private final boolean isRevertReasonEnabled;
  private final OptionalLong ecip1017EraRounds;
  private final EvmConfiguration evmConfiguration;
  private final MiningParameters miningParameters;

  public MainnetProtocolSpecFactory(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt evmStackSize,
      final boolean isRevertReasonEnabled,
      final OptionalLong ecip1017EraRounds,
      final EvmConfiguration evmConfiguration,
      final MiningParameters miningParameters) {
    this.chainId = chainId;
    this.contractSizeLimit = contractSizeLimit;
    this.evmStackSize = evmStackSize;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.ecip1017EraRounds = ecip1017EraRounds;
    this.evmConfiguration = evmConfiguration;
    this.miningParameters = miningParameters;
  }

  public ProtocolSpecBuilder frontierDefinition() {
    return MainnetProtocolSpecs.frontierDefinition(
        contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder homesteadDefinition() {
    return MainnetProtocolSpecs.homesteadDefinition(
        contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder daoRecoveryInitDefinition() {
    return MainnetProtocolSpecs.daoRecoveryInitDefinition(
        contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder daoRecoveryTransitionDefinition() {
    return MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
        contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder tangerineWhistleDefinition() {
    return MainnetProtocolSpecs.tangerineWhistleDefinition(
        contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder spuriousDragonDefinition() {
    return MainnetProtocolSpecs.spuriousDragonDefinition(
        chainId, contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder byzantiumDefinition() {
    return MainnetProtocolSpecs.byzantiumDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder constantinopleDefinition() {
    return MainnetProtocolSpecs.constantinopleDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder petersburgDefinition() {
    return MainnetProtocolSpecs.petersburgDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder istanbulDefinition() {
    return MainnetProtocolSpecs.istanbulDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder muirGlacierDefinition() {
    return MainnetProtocolSpecs.muirGlacierDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder berlinDefinition() {
    return MainnetProtocolSpecs.berlinDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, evmConfiguration);
  }

  public ProtocolSpecBuilder londonDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.londonDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningParameters);
  }

  public ProtocolSpecBuilder arrowGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.arrowGlacierDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningParameters);
  }

  public ProtocolSpecBuilder grayGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.grayGlacierDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
  }

  public ProtocolSpecBuilder parisDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.parisDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
  }

  public ProtocolSpecBuilder shanghaiDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.shanghaiDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
  }

  public ProtocolSpecBuilder cancunDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.cancunDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration,
        miningParameters);
  }

  public ProtocolSpecBuilder pragueDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.pragueDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
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
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
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
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        evmConfiguration);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Classic Protocol Specs
  public ProtocolSpecBuilder dieHardDefinition() {
    return ClassicProtocolSpecs.dieHardDefinition(
        chainId, contractSizeLimit, evmStackSize, evmConfiguration);
  }

  public ProtocolSpecBuilder gothamDefinition() {
    return ClassicProtocolSpecs.gothamDefinition(
        chainId, contractSizeLimit, evmStackSize, ecip1017EraRounds, evmConfiguration);
  }

  public ProtocolSpecBuilder defuseDifficultyBombDefinition() {
    return ClassicProtocolSpecs.defuseDifficultyBombDefinition(
        chainId, contractSizeLimit, evmStackSize, ecip1017EraRounds, evmConfiguration);
  }

  public ProtocolSpecBuilder atlantisDefinition() {
    return ClassicProtocolSpecs.atlantisDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder aghartaDefinition() {
    return ClassicProtocolSpecs.aghartaDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder phoenixDefinition() {
    return ClassicProtocolSpecs.phoenixDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder thanosDefinition() {
    return ClassicProtocolSpecs.thanosDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder magnetoDefinition() {
    return ClassicProtocolSpecs.magnetoDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder mystiqueDefinition() {
    return ClassicProtocolSpecs.mystiqueDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }

  public ProtocolSpecBuilder spiralDefinition() {
    return ClassicProtocolSpecs.spiralDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        evmConfiguration);
  }
}
