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

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

import io.vertx.core.json.JsonObject;

/** Defines the protocol behaviours for a blockchain using IBFT. */
public class IbftProtocolSchedule {

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 1;

  public static ProtocolSchedule<IbftContext> create(final JsonObject config) {
    final long spuriousDragonBlock = config.getLong("spuriousDragonBlock", 0L);
    final Optional<JsonObject> ibftConfig = Optional.ofNullable(config.getJsonObject("ibft"));
    final int chainId = config.getInteger("chainId", 1);
    final long epochLength = getEpochLength(ibftConfig);
    final long blockPeriod =
        ibftConfig
            .map(iC -> iC.getInteger("blockPeriodSeconds"))
            .orElse(DEFAULT_BLOCK_PERIOD_SECONDS);

    final MutableProtocolSchedule<IbftContext> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(
        spuriousDragonBlock,
        IbftProtocolSpecs.spuriousDragon(blockPeriod, epochLength, chainId, protocolSchedule));
    return protocolSchedule;
  }

  public static long getEpochLength(final Optional<JsonObject> ibftConfig) {
    return ibftConfig.map(conf -> conf.getLong("epochLength")).orElse(DEFAULT_EPOCH_LENGTH);
  }
}
