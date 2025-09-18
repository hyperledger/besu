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

import static org.hyperledger.besu.ethereum.mainnet.milestones.MilestoneDefinition.createBlockNumberMilestone;
import static org.hyperledger.besu.ethereum.mainnet.milestones.MilestoneDefinition.createTimestampMilestone;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Provides milestone definitions for the Ethereum Mainnet and Classic networks. */
public class MilestoneDefinitions {

  public static List<MilestoneDefinition> createMilestoneDefinitions(
      final MainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    List<MilestoneDefinition> milestones = new ArrayList<>();
    milestones.addAll(createMainnetMilestoneDefinitions(specFactory, config));
    milestones.addAll(createClassicMilestoneDefinitions(specFactory, config));
    return milestones;
  }

  /**
   * Returns the milestone definitions for the Classic network.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of milestone definitions for the Classic network
   */
  private static List<MilestoneDefinition> createClassicMilestoneDefinitions(
      final MainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return List.of(
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.CLASSIC_TANGERINE_WHISTLE,
            config.getEcip1015BlockNumber(),
            specFactory::tangerineWhistleDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.DIE_HARD,
            config.getDieHardBlockNumber(),
            specFactory::dieHardDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.GOTHAM,
            config.getGothamBlockNumber(),
            specFactory::gothamDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.DEFUSE_DIFFICULTY_BOMB,
            config.getDefuseDifficultyBombBlockNumber(),
            specFactory::defuseDifficultyBombDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.ATLANTIS,
            config.getAtlantisBlockNumber(),
            specFactory::atlantisDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.AGHARTA,
            config.getAghartaBlockNumber(),
            specFactory::aghartaDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.PHOENIX,
            config.getPhoenixBlockNumber(),
            specFactory::phoenixDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.THANOS,
            config.getThanosBlockNumber(),
            specFactory::thanosDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.MAGNETO,
            config.getMagnetoBlockNumber(),
            specFactory::magnetoDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.MYSTIQUE,
            config.getMystiqueBlockNumber(),
            specFactory::mystiqueDefinition),
        createBlockNumberMilestone(
            HardforkId.ClassicHardforkId.SPIRAL,
            config.getSpiralBlockNumber(),
            specFactory::spiralDefinition));
  }

  /**
   * Creates the milestone definitions for the Mainnet networks.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of milestone definitions for the Mainnet
   */
  private static List<MilestoneDefinition> createMainnetMilestoneDefinitions(
      final MainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    List<MilestoneDefinition> milestones = new ArrayList<>();
    // Add block number milestones first
    milestones.addAll(createMainnetBlockNumberMilestones(specFactory, config));
    // Then add timestamp milestones
    milestones.addAll(createMainnetTimestampMilestones(specFactory, config));
    return milestones;
  }

  /**
   * Creates block number milestones for the Mainnet.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of block number milestones
   */
  private static List<MilestoneDefinition> createMainnetBlockNumberMilestones(
      final MainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return List.of(
        createBlockNumberMilestone(
            MainnetHardforkId.FRONTIER, OptionalLong.of(0), specFactory::frontierDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.HOMESTEAD,
            config.getHomesteadBlockNumber(),
            specFactory::homesteadDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.TANGERINE_WHISTLE,
            config.getTangerineWhistleBlockNumber(),
            specFactory::tangerineWhistleDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.SPURIOUS_DRAGON,
            config.getSpuriousDragonBlockNumber(),
            specFactory::spuriousDragonDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.BYZANTIUM,
            config.getByzantiumBlockNumber(),
            specFactory::byzantiumDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.CONSTANTINOPLE,
            config.getConstantinopleBlockNumber(),
            specFactory::constantinopleDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.PETERSBURG,
            config.getPetersburgBlockNumber(),
            specFactory::petersburgDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.ISTANBUL,
            config.getIstanbulBlockNumber(),
            specFactory::istanbulDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.MUIR_GLACIER,
            config.getMuirGlacierBlockNumber(),
            specFactory::muirGlacierDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.BERLIN, config.getBerlinBlockNumber(), specFactory::berlinDefinition),
        createBlockNumberMilestone(
            MainnetHardforkId.LONDON,
            config.getLondonBlockNumber(),
            () -> specFactory.londonDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.ARROW_GLACIER,
            config.getArrowGlacierBlockNumber(),
            () -> specFactory.arrowGlacierDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.GRAY_GLACIER,
            config.getGrayGlacierBlockNumber(),
            () -> specFactory.grayGlacierDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.PARIS,
            config.getMergeNetSplitBlockNumber(),
            () -> specFactory.parisDefinition(config)));
  }

  /**
   * Creates timestamp milestones for the Mainnet.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of timestamp milestones
   */
  private static List<MilestoneDefinition> createMainnetTimestampMilestones(
      final MainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return List.of(
        createTimestampMilestone(
            MainnetHardforkId.SHANGHAI,
            config.getShanghaiTime(),
            () -> specFactory.shanghaiDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.CANCUN,
            config.getCancunTime(),
            () -> specFactory.cancunDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.CANCUN_EOF,
            config.getCancunEOFTime(),
            () -> specFactory.cancunEOFDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.PRAGUE,
            config.getPragueTime(),
            () -> specFactory.pragueDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.OSAKA,
            config.getOsakaTime(),
            () -> specFactory.osakaDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.AMSTERDAM,
            config.getOsakaTime(),
            () -> specFactory.amsterdamDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.BPO1, config.getBpo1Time(), () -> specFactory.bpo1Definition(config)),
        createTimestampMilestone(
            MainnetHardforkId.BPO2, config.getBpo2Time(), () -> specFactory.bpo2Definition(config)),
        createTimestampMilestone(
            MainnetHardforkId.BPO3, config.getBpo3Time(), () -> specFactory.bpo3Definition(config)),
        createTimestampMilestone(
            MainnetHardforkId.BPO4, config.getBpo4Time(), () -> specFactory.bpo4Definition(config)),
        createTimestampMilestone(
            MainnetHardforkId.BPO5, config.getBpo5Time(), () -> specFactory.bpo5Definition(config)),
        createTimestampMilestone(
            MainnetHardforkId.FUTURE_EIPS,
            config.getFutureEipsTime(),
            () -> specFactory.futureEipsDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.EXPERIMENTAL_EIPS,
            config.getExperimentalEipsTime(),
            () -> specFactory.experimentalEipsDefinition(config)));
  }
}
