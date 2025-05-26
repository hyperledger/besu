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
import static org.hyperledger.besu.ethereum.mainnet.milestones.MilestoneDefinitionConfig.createTimestampMilestone;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecFactory;

import java.util.List;
import java.util.OptionalLong;

/**
 * Provides the milestone definitions for the Mainnet networks.
 */
public class MainnetMilestoneDefinitionProvider implements MilestoneDefinitionProvider {
  @Override
  public List<MilestoneDefinitionConfig> getMilestoneDefinitions(
      MainnetProtocolSpecFactory specFactory, GenesisConfigOptions config) {
    return List.of(
        createBlockNumberMilestone(
            MainnetHardforkId.FRONTIER, OptionalLong.of(0), specFactory.frontierDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.HOMESTEAD,
            config.getHomesteadBlockNumber(),
            specFactory.homesteadDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.TANGERINE_WHISTLE,
            config.getTangerineWhistleBlockNumber(),
            specFactory.tangerineWhistleDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.SPURIOUS_DRAGON,
            config.getSpuriousDragonBlockNumber(),
            specFactory.spuriousDragonDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.BYZANTIUM,
            config.getByzantiumBlockNumber(),
            specFactory.byzantiumDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.CONSTANTINOPLE,
            config.getConstantinopleBlockNumber(),
            specFactory.constantinopleDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.PETERSBURG,
            config.getPetersburgBlockNumber(),
            specFactory.petersburgDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.ISTANBUL,
            config.getIstanbulBlockNumber(),
            specFactory.istanbulDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.MUIR_GLACIER,
            config.getMuirGlacierBlockNumber(),
            specFactory.muirGlacierDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.BERLIN,
            config.getBerlinBlockNumber(),
            specFactory.berlinDefinition()),
        createBlockNumberMilestone(
            MainnetHardforkId.LONDON,
            config.getLondonBlockNumber(),
            specFactory.londonDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.ARROW_GLACIER,
            config.getArrowGlacierBlockNumber(),
            specFactory.arrowGlacierDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.GRAY_GLACIER,
            config.getGrayGlacierBlockNumber(),
            specFactory.grayGlacierDefinition(config)),
        createBlockNumberMilestone(
            MainnetHardforkId.PARIS,
            config.getMergeNetSplitBlockNumber(),
            specFactory.parisDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.SHANGHAI,
            config.getShanghaiTime(),
            specFactory.shanghaiDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.CANCUN, config.getCancunTime(), specFactory.cancunDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.CANCUN_EOF,
            config.getCancunEOFTime(),
            specFactory.cancunEOFDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.PRAGUE, config.getPragueTime(), specFactory.pragueDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.OSAKA, config.getOsakaTime(), specFactory.osakaDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.FUTURE_EIPS,
            config.getFutureEipsTime(),
            specFactory.futureEipsDefinition(config)),
        createTimestampMilestone(
            MainnetHardforkId.EXPERIMENTAL_EIPS,
            config.getExperimentalEipsTime(),
            specFactory.experimentalEipsDefinition(config)));
  }
}
