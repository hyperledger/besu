package net.consensys.pantheon.ethereum.development;

import static net.consensys.pantheon.ethereum.mainnet.MainnetTransactionValidator.NO_CHAIN_ID;

import net.consensys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;

import io.vertx.core.json.JsonObject;

/**
 * A mock ProtocolSchedule which behaves similarly to Frontier (but for all blocks), albeit with a
 * much reduced difficulty (which supports testing on CPU alone).
 */
public class DevelopmentProtocolSchedule {

  public static ProtocolSchedule<Void> create(final JsonObject config) {
    final Integer chainId = config.getInteger("chainId", NO_CHAIN_ID);
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, DevelopmentProtocolSpecs.first(chainId, protocolSchedule));
    return protocolSchedule;
  }
}
