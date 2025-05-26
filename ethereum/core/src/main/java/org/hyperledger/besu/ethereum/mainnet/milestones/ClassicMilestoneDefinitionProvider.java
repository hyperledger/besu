/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet.milestones;

import static org.hyperledger.besu.ethereum.mainnet.milestones.MilestoneDefinitionConfig.createBlockNumberMilestone;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecFactory;

import java.util.List;

/**
 * Provides the milestone definitions for the Classic networks.
 */
public class ClassicMilestoneDefinitionProvider implements MilestoneDefinitionProvider {
  @Override
  public List<MilestoneDefinitionConfig> getMilestoneDefinitions(
      MainnetProtocolSpecFactory specFactory, GenesisConfigOptions config) {
    return List.of(
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.CLASSIC_TANGERINE_WHISTLE,
            config.getEcip1015BlockNumber(),
            specFactory.tangerineWhistleDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.DIE_HARD,
            config.getDieHardBlockNumber(),
            specFactory.dieHardDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.GOTHAM,
            config.getGothamBlockNumber(),
            specFactory.gothamDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.DEFUSE_DIFFICULTY_BOMB,
            config.getDefuseDifficultyBombBlockNumber(),
            specFactory.defuseDifficultyBombDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.ATLANTIS,
            config.getAtlantisBlockNumber(),
            specFactory.atlantisDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.AGHARTA,
            config.getAghartaBlockNumber(),
            specFactory.aghartaDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.PHOENIX,
            config.getPhoenixBlockNumber(),
            specFactory.phoenixDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.THANOS,
            config.getThanosBlockNumber(),
            specFactory.thanosDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.MAGNETO,
            config.getMagnetoBlockNumber(),
            specFactory.magnetoDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.MYSTIQUE,
            config.getMystiqueBlockNumber(),
            specFactory.mystiqueDefinition()),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.SPIRAL,
            config.getSpiralBlockNumber(),
            specFactory.spiralDefinition()));
  }
}
