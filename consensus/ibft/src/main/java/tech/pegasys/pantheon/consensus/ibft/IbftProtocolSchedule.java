package tech.pegasys.pantheon.consensus.ibft;

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
