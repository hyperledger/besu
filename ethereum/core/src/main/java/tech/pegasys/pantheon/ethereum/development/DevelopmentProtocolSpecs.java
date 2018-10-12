package tech.pegasys.pantheon.ethereum.development;

import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

/**
 * Provides a protocol specification which is suitable for use on private, PoW networks, where block
 * mining is performed on CPUs alone.
 */
public class DevelopmentProtocolSpecs {

  /*
   * The DevelopmentProtocolSpecification is the same as the byzantium spec, but with a much reduced
   * difficulty calculator (to support CPU mining).
   */
  public static ProtocolSpec<Void> first(
      final Integer chainId, final ProtocolSchedule<Void> protocolSchedule) {
    return MainnetProtocolSpecs.byzantiumDefinition(chainId)
        .difficultyCalculator(DevelopmentDifficultyCalculators.DEVELOPER)
        .name("first")
        .build(protocolSchedule);
  }
}
