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
package tech.pegasys.pantheon.ethereum.mainnet;

import io.vertx.core.json.JsonObject;

/** Provides {@link ProtocolSpec} lookups for mainnet hard forks. */
public class MainnetProtocolSchedule {

  private static final long DEFAULT_HOMESTEAD_BLOCK_NUMBER = 1_150_000L;
  private static final long DEFAULT_DAO_BLOCK_NUMBER = 1_920_000L;
  private static final long DEFAULT_TANGERINE_WHISTLE_BLOCK_NUMBER = 2_463_000L;
  private static final long DEFAULT_SPURIOUS_DRAGON_BLOCK_NUMBER = 2_675_000L;
  private static final long DEFAULT_BYZANTIUM_BLOCK_NUMBER = 4_730_000L;
  // Start of Constantinople has not yet been set.
  private static final long DEFAULT_CONSTANTINOPLE_BLOCK_NUMBER = -1L;
  public static final int DEFAULT_CHAIN_ID = 1;

  /**
   * Creates a mainnet protocol schedule with milestones starting at the specified block numbers
   *
   * @param homesteadBlockNumber Block number at which to start the homestead fork
   * @param daoBlockNumber Block number at which to start the dao fork
   * @param tangerineWhistleBlockNumber Block number at which to start the tangerine whistle fork
   * @param spuriousDragonBlockNumber Block number at which to start the spurious dragon fork
   * @param byzantiumBlockNumber Block number at which to start the byzantium fork
   * @param constantinopleBlockNumber Block number at which to start the constantinople fork
   * @param chainId ID of the blockchain
   * @return MainnetProtocolSchedule return newly instantiated protocol schedule
   */
  public static ProtocolSchedule<Void> create(
      final long homesteadBlockNumber,
      final long daoBlockNumber,
      final long tangerineWhistleBlockNumber,
      final long spuriousDragonBlockNumber,
      final long byzantiumBlockNumber,
      final long constantinopleBlockNumber,
      final int chainId) {

    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, MainnetProtocolSpecs.frontier(protocolSchedule));
    final ProtocolSpec<Void> homestead = MainnetProtocolSpecs.homestead(protocolSchedule);
    protocolSchedule.putMilestone(homesteadBlockNumber, homestead);
    if (daoBlockNumber != 0) {
      protocolSchedule.putMilestone(
          daoBlockNumber, MainnetProtocolSpecs.daoRecoveryInit(protocolSchedule));
      protocolSchedule.putMilestone(
          daoBlockNumber + 1, MainnetProtocolSpecs.daoRecoveryTransition(protocolSchedule));
      protocolSchedule.putMilestone(daoBlockNumber + 10, homestead);
    }
    protocolSchedule.putMilestone(
        tangerineWhistleBlockNumber, MainnetProtocolSpecs.tangerineWhistle(protocolSchedule));
    protocolSchedule.putMilestone(
        spuriousDragonBlockNumber, MainnetProtocolSpecs.spuriousDragon(chainId, protocolSchedule));
    protocolSchedule.putMilestone(
        byzantiumBlockNumber, MainnetProtocolSpecs.byzantium(chainId, protocolSchedule));

    if (constantinopleBlockNumber >= 0) {
      protocolSchedule.putMilestone(
          constantinopleBlockNumber,
          MainnetProtocolSpecs.constantinople(chainId, protocolSchedule));
    }

    return protocolSchedule;
  }

  public static ProtocolSchedule<Void> create() {
    return create(
        DEFAULT_HOMESTEAD_BLOCK_NUMBER,
        DEFAULT_DAO_BLOCK_NUMBER,
        DEFAULT_TANGERINE_WHISTLE_BLOCK_NUMBER,
        DEFAULT_SPURIOUS_DRAGON_BLOCK_NUMBER,
        DEFAULT_BYZANTIUM_BLOCK_NUMBER,
        DEFAULT_CONSTANTINOPLE_BLOCK_NUMBER,
        DEFAULT_CHAIN_ID);
  }

  /**
   * Create a Mainnet protocol schedule from a config object
   *
   * @param config {@link JsonObject} containing the config options for the milestone starting
   *     points
   * @return A configured mainnet protocol schedule
   */
  public static ProtocolSchedule<Void> fromConfig(final JsonObject config) {
    final long homesteadBlockNumber =
        config.getLong("homesteadBlock", DEFAULT_HOMESTEAD_BLOCK_NUMBER);
    final long daoBlockNumber = config.getLong("daoForkBlock", DEFAULT_DAO_BLOCK_NUMBER);
    final long tangerineWhistleBlockNumber =
        config.getLong("eip150Block", DEFAULT_TANGERINE_WHISTLE_BLOCK_NUMBER);
    final long spuriousDragonBlockNumber =
        config.getLong("eip158Block", DEFAULT_SPURIOUS_DRAGON_BLOCK_NUMBER);
    final long byzantiumBlockNumber =
        config.getLong("byzantiumBlock", DEFAULT_BYZANTIUM_BLOCK_NUMBER);
    final long constantinopleBlockNumber =
        config.getLong("constantinopleBlock", DEFAULT_CONSTANTINOPLE_BLOCK_NUMBER);
    final int chainId = config.getInteger("chainId", DEFAULT_CHAIN_ID);
    return create(
        homesteadBlockNumber,
        daoBlockNumber,
        tangerineWhistleBlockNumber,
        spuriousDragonBlockNumber,
        byzantiumBlockNumber,
        constantinopleBlockNumber,
        chainId);
  }
}
