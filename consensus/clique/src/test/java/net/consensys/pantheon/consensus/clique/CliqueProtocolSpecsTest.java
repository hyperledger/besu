package net.consensys.pantheon.consensus.clique;

import static org.assertj.core.api.Java6Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import net.consensys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;

import org.junit.Test;

public class CliqueProtocolSpecsTest {

  CliqueProtocolSpecs protocolSpecs =
      new CliqueProtocolSpecs(
          15, 30_000, 5, AddressHelpers.ofValue(5), new MutableProtocolSchedule<>());

  @Test
  public void homsteadParametersAlignWithMainnetWithAdjustments() {
    final ProtocolSpec<CliqueContext> homestead = protocolSpecs.homestead();

    assertThat(homestead.getName()).isEqualTo("Homestead");
    assertThat(homestead.getBlockReward()).isEqualTo(Wei.ZERO);
    assertThat(homestead.getDifficultyCalculator()).isInstanceOf(CliqueDifficultyCalculator.class);
  }

  @Test
  public void allSpecsInheritFromMainnetCounterparts() {
    final ProtocolSchedule<Void> mainnetProtocolSchedule = new MutableProtocolSchedule<>();

    assertThat(protocolSpecs.frontier().getName())
        .isEqualTo(MainnetProtocolSpecs.frontier(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.homestead().getName())
        .isEqualTo(MainnetProtocolSpecs.homestead(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.tangerineWhistle().getName())
        .isEqualTo(MainnetProtocolSpecs.tangerineWhistle(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.spuriousDragon().getName())
        .isEqualTo(MainnetProtocolSpecs.spuriousDragon(1, mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.byzantium().getName())
        .isEqualTo(MainnetProtocolSpecs.byzantium(1, mainnetProtocolSchedule).getName());
  }
}
