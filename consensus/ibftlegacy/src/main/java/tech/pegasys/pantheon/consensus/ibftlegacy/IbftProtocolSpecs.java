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

import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockImporter;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;

/** Factory for producing Ibft protocol specs for given configurations and known fork points */
public class IbftProtocolSpecs {

  /**
   * Produce the ProtocolSpec for an IBFT chain that uses spurious dragon milestone configuration
   *
   * @param secondsBetweenBlocks the block period in seconds
   * @param epochLength the number of blocks in each epoch
   * @param chainId the id of the Chain.
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return a configured ProtocolSpec for dealing with IBFT blocks
   */
  public static ProtocolSpec<IbftContext> spuriousDragon(
      final long secondsBetweenBlocks,
      final long epochLength,
      final int chainId,
      final ProtocolSchedule<IbftContext> protocolSchedule) {
    final EpochManager epochManager = new EpochManager(epochLength);
    return MainnetProtocolSpecs.spuriousDragonDefinition(chainId)
        .<IbftContext>changeConsensusContextType(
            difficultyCalculator -> ibftBlockHeaderValidator(secondsBetweenBlocks),
            difficultyCalculator -> ibftBlockHeaderValidator(secondsBetweenBlocks),
            MainnetBlockBodyValidator::new,
            (blockHeaderValidator, blockBodyValidator, blockProcessor) ->
                new IbftBlockImporter(
                    new MainnetBlockImporter<>(
                        blockHeaderValidator, blockBodyValidator, blockProcessor),
                    new IbftVoteTallyUpdater(epochManager)),
            (time, parent, protocolContext) -> BigInteger.ONE)
        .blockReward(Wei.ZERO)
        .blockHashFunction(IbftBlockHashing::calculateHashOfIbftBlockOnChain)
        .name("IBFT")
        .build(protocolSchedule);
  }
}
