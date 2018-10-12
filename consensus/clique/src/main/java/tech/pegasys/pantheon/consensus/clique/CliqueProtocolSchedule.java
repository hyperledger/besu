package net.consensys.pantheon.consensus.clique;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.core.Util;
import net.consensys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

import io.vertx.core.json.JsonObject;

/** Defines the protocol behaviours for a blockchain using Clique. */
public class CliqueProtocolSchedule extends MutableProtocolSchedule<CliqueContext> {

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 1;
  private static final int DEFAULT_CHAIN_ID = 4;

  public static ProtocolSchedule<CliqueContext> create(
      final JsonObject config, final KeyPair nodeKeys) {

    // Get Config Data
    final Optional<JsonObject> cliqueConfig = Optional.ofNullable(config.getJsonObject("clique"));
    final long epochLength =
        cliqueConfig.map(cc -> cc.getLong("epochLength")).orElse(DEFAULT_EPOCH_LENGTH);
    final long blockPeriod =
        cliqueConfig
            .map(cc -> cc.getInteger("blockPeriodSeconds"))
            .orElse(DEFAULT_BLOCK_PERIOD_SECONDS);
    final int chainId = config.getInteger("chainId", DEFAULT_CHAIN_ID);

    final MutableProtocolSchedule<CliqueContext> protocolSchedule = new CliqueProtocolSchedule();

    // TODO(tmm) replace address with passed in node data (coming later)
    final CliqueProtocolSpecs specs =
        new CliqueProtocolSpecs(
            blockPeriod,
            epochLength,
            chainId,
            Util.publicKeyToAddress(nodeKeys.getPublicKey()),
            protocolSchedule);

    protocolSchedule.putMilestone(0, specs.frontier());

    final Long homesteadBlockNumber = config.getLong("homesteadBlock");
    if (homesteadBlockNumber != null) {
      protocolSchedule.putMilestone(homesteadBlockNumber, specs.homestead());
    }

    final Long tangerineWhistleBlockNumber = config.getLong("eip150Block");
    if (tangerineWhistleBlockNumber != null) {
      protocolSchedule.putMilestone(tangerineWhistleBlockNumber, specs.tangerineWhistle());
    }

    final Long spuriousDragonBlockNumber = config.getLong("eip158Block");
    if (spuriousDragonBlockNumber != null) {
      protocolSchedule.putMilestone(spuriousDragonBlockNumber, specs.spuriousDragon());
    }

    final Long byzantiumBlockNumber = config.getLong("byzantiumBlock");
    if (byzantiumBlockNumber != null) {
      protocolSchedule.putMilestone(byzantiumBlockNumber, specs.byzantium());
    }

    return protocolSchedule;
  }
}
