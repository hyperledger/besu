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

  public MainnetProtocolSpecFactory(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt evmStackSize,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final OptionalLong ecip1017EraRounds) {
    this.chainId = chainId;
    this.contractSizeLimit = contractSizeLimit;
    this.evmStackSize = evmStackSize;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.quorumCompatibilityMode = quorumCompatibilityMode;
    this.ecip1017EraRounds = ecip1017EraRounds;
  }

  public ProtocolSpecBuilder frontierDefinition() {
    return MainnetProtocolSpecs.frontierDefinition(
        contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder homesteadDefinition() {
    return MainnetProtocolSpecs.homesteadDefinition(
        contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder daoRecoveryInitDefinition() {
    return MainnetProtocolSpecs.daoRecoveryInitDefinition(
        contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder daoRecoveryTransitionDefinition() {
    return MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
        contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder tangerineWhistleDefinition() {
    return MainnetProtocolSpecs.tangerineWhistleDefinition(
        contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder spuriousDragonDefinition() {
    return MainnetProtocolSpecs.spuriousDragonDefinition(
        chainId, contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder byzantiumDefinition() {
    return MainnetProtocolSpecs.byzantiumDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder constantinopleDefinition() {
    return MainnetProtocolSpecs.constantinopleDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder petersburgDefinition() {
    return MainnetProtocolSpecs.petersburgDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder istanbulDefinition() {
    return MainnetProtocolSpecs.istanbulDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder muirGlacierDefinition() {
    return MainnetProtocolSpecs.muirGlacierDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder berlinDefinition() {
    return MainnetProtocolSpecs.berlinDefinition(
        chainId, contractSizeLimit, evmStackSize, isRevertReasonEnabled, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder londonDefinition(final GenesisConfigOptions genesisConfigOptions) {
    return MainnetProtocolSpecs.londonDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        genesisConfigOptions,
        quorumCompatibilityMode);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Classic Protocol Specs
  public ProtocolSpecBuilder dieHardDefinition() {
    return ClassicProtocolSpecs.dieHardDefinition(
        chainId, contractSizeLimit, evmStackSize, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder gothamDefinition() {
    return ClassicProtocolSpecs.gothamDefinition(
        chainId, contractSizeLimit, evmStackSize, ecip1017EraRounds, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder defuseDifficultyBombDefinition() {
    return ClassicProtocolSpecs.defuseDifficultyBombDefinition(
        chainId, contractSizeLimit, evmStackSize, ecip1017EraRounds, quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder atlantisDefinition() {
    return ClassicProtocolSpecs.atlantisDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder aghartaDefinition() {
    return ClassicProtocolSpecs.aghartaDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder phoenixDefinition() {
    return ClassicProtocolSpecs.phoenixDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder thanosDefinition() {
    return ClassicProtocolSpecs.thanosDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder magnetoDefinition() {
    return ClassicProtocolSpecs.magnetoDefinition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }

  public ProtocolSpecBuilder ecip1049Definition() {
    return ClassicProtocolSpecs.ecip1049Definition(
        chainId,
        contractSizeLimit,
        evmStackSize,
        isRevertReasonEnabled,
        ecip1017EraRounds,
        quorumCompatibilityMode);
  }
}
