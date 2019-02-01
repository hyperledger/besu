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
package tech.pegasys.pantheon.consensus.ibftlegacy;

import static tech.pegasys.pantheon.consensus.ibftlegacy.IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator;

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.config.IbftConfigOptions;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockImporter;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.ethereum.MainnetBlockValidator;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolScheduleBuilder;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;

/** Defines the protocol behaviours for a blockchain using IBFT. */
public class IbftProtocolSchedule {

  private static final int DEFAULT_CHAIN_ID = 1;

  public static ProtocolSchedule<IbftContext> create(final GenesisConfigOptions config) {
    final IbftConfigOptions ibftConfig = config.getIbftLegacyConfigOptions();
    final long epochLength = ibftConfig.getEpochLength();
    final long blockPeriod = ibftConfig.getBlockPeriodSeconds();
    final EpochManager epochManager = new EpochManager(epochLength);

    return new ProtocolScheduleBuilder<>(
            config,
            DEFAULT_CHAIN_ID,
            builder -> applyIbftChanges(blockPeriod, epochManager, builder),
            PrivacyParameters.noPrivacy())
        .createProtocolSchedule();
  }

  private static ProtocolSpecBuilder<IbftContext> applyIbftChanges(
      final long secondsBetweenBlocks,
      final EpochManager epochManager,
      final ProtocolSpecBuilder<Void> builder) {
    return builder
        .<IbftContext>changeConsensusContextType(
            difficultyCalculator -> ibftBlockHeaderValidator(secondsBetweenBlocks),
            difficultyCalculator -> ibftBlockHeaderValidator(secondsBetweenBlocks),
            MainnetBlockBodyValidator::new,
            MainnetBlockValidator::new,
            (blockValidator) ->
                new IbftBlockImporter(
                    new MainnetBlockImporter<>(blockValidator),
                    new VoteTallyUpdater(epochManager, new IbftLegacyBlockInterface())),
            (time, parent, protocolContext) -> BigInteger.ONE)
        .blockReward(Wei.ZERO)
        .blockHashFunction(IbftBlockHashing::calculateHashOfIbftBlockOnChain);
  }
}
