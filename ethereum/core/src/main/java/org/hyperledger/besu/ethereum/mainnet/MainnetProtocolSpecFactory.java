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

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class MainnetProtocolSpecFactory {

  private final Optional<BigInteger> chainId;
  private final OptionalInt contractSizeLimit;
  private final OptionalInt evmStackSize;
  private final boolean isRevertReasonEnabled;
  private final boolean quorumCompatibilityMode;
  private final OptionalLong ecip1017EraRounds;
  private final EvmConfiguration evmConfiguration;
  private final boolean validatePrivateTransactions;

  public MainnetProtocolSpecFactory(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt evmStackSize,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final OptionalLong ecip1017EraRounds,
      final EvmConfiguration evmConfiguration,
      final boolean validatePrivateTransactions) {
    this.chainId = chainId;
    this.contractSizeLimit = contractSizeLimit;
    this.evmStackSize = evmStackSize;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.quorumCompatibilityMode = quorumCompatibilityMode;
    this.ecip1017EraRounds = ecip1017EraRounds;
    this.evmConfiguration = evmConfiguration;
    this.validatePrivateTransactions = validatePrivateTransactions;
  }

  public ProtocolSpecBuilder frontierDefinition() {
    return MainnetProtocolSpecs.frontierDefinition(
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder homesteadDefinition() {
    return MainnetProtocolSpecs.homesteadDefinition(
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder daoRecoveryInitDefinition() {
    return MainnetProtocolSpecs.daoRecoveryInitDefinition(
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder daoRecoveryTransitionDefinition() {
    return MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder tangerineWhistleDefinition() {
    return MainnetProtocolSpecs.tangerineWhistleDefinition(
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder spuriousDragonDefinition() {
    return MainnetProtocolSpecs.spuriousDragonDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder byzantiumDefinition() {
    return MainnetProtocolSpecs.byzantiumDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder constantinopleDefinition() {
    return MainnetProtocolSpecs.constantinopleDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder petersburgDefinition() {
    return MainnetProtocolSpecs.petersburgDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder istanbulDefinition() {
    return MainnetProtocolSpecs.istanbulDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder muirGlacierDefinition() {
    return MainnetProtocolSpecs.muirGlacierDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder berlinDefinition() {
    return MainnetProtocolSpecs.berlinDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder londonDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.londonDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder arrowGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.arrowGlacierDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder grayGlacierDefinition(
      final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.grayGlacierDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder parisDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.parisDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder shandongDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.shandongDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Classic Protocol Specs
  public ProtocolSpecBuilder dieHardDefinition() {
    return ClassicProtocolSpecs.dieHardDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder gothamDefinition() {
    return ClassicProtocolSpecs.gothamDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder defuseDifficultyBombDefinition() {
    return ClassicProtocolSpecs.defuseDifficultyBombDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder atlantisDefinition() {
    return ClassicProtocolSpecs.atlantisDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder aghartaDefinition() {
    return ClassicProtocolSpecs.aghartaDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder phoenixDefinition() {
    return ClassicProtocolSpecs.phoenixDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder thanosDefinition() {
    return ClassicProtocolSpecs.thanosDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder magnetoDefinition() {
    return ClassicProtocolSpecs.magnetoDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder mystiqueDefinition() {
    return ClassicProtocolSpecs.mystiqueDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }

  public ProtocolSpecBuilder ecip1049Definition() {
    return ClassicProtocolSpecs.ecip1049Definition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode,
        evmConfiguration,
        validatePrivateTransactions);
  }
}
