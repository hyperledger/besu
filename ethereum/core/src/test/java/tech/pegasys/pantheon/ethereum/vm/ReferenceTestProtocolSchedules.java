package tech.pegasys.pantheon.ethereum.vm;

import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;

public class ReferenceTestProtocolSchedules {

  private static final int CHAIN_ID = 1;

  public static ReferenceTestProtocolSchedules create() {
    final ImmutableMap.Builder<String, ProtocolSchedule<Void>> builder = ImmutableMap.builder();
    builder.put("Frontier", createSchedule(MainnetProtocolSpecs::frontier));
    builder.put("FrontierToHomesteadAt5", frontierToHomesteadAt5());
    builder.put("Homestead", createSchedule(MainnetProtocolSpecs::homestead));
    builder.put("HomesteadToEIP150At5", homesteadToEip150At5());
    builder.put("HomesteadToDaoAt5", homesteadToDaoAt5());
    builder.put("EIP150", createSchedule(MainnetProtocolSpecs::tangerineWhistle));
    builder.put(
        "EIP158",
        createSchedule(
            protocolSpecLookup ->
                MainnetProtocolSpecs.spuriousDragon(CHAIN_ID, protocolSpecLookup)));
    builder.put("EIP158ToByzantiumAt5", eip158ToByzantiumAt5());
    builder.put(
        "Byzantium",
        createSchedule(
            protocolSpecLookup -> MainnetProtocolSpecs.byzantium(CHAIN_ID, protocolSpecLookup)));
    return new ReferenceTestProtocolSchedules(builder.build());
  }

  private final Map<String, ProtocolSchedule<Void>> schedules;

  private ReferenceTestProtocolSchedules(final Map<String, ProtocolSchedule<Void>> schedules) {
    this.schedules = schedules;
  }

  public ProtocolSchedule<Void> getByName(final String name) {
    return schedules.get(name);
  }

  private static ProtocolSchedule<Void> createSchedule(
      final Function<ProtocolSchedule<Void>, ProtocolSpec<Void>> specCreator) {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, specCreator.apply(protocolSchedule));
    return protocolSchedule;
  }

  private static ProtocolSchedule<Void> frontierToHomesteadAt5() {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, MainnetProtocolSpecs.frontier(protocolSchedule));
    protocolSchedule.putMilestone(5, MainnetProtocolSpecs.homestead(protocolSchedule));
    return protocolSchedule;
  }

  private static ProtocolSchedule<Void> homesteadToEip150At5() {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, MainnetProtocolSpecs.homestead(protocolSchedule));
    protocolSchedule.putMilestone(5, MainnetProtocolSpecs.tangerineWhistle(protocolSchedule));
    return protocolSchedule;
  }

  private static ProtocolSchedule<Void> homesteadToDaoAt5() {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    final ProtocolSpec<Void> homestead = MainnetProtocolSpecs.homestead(protocolSchedule);
    protocolSchedule.putMilestone(0, homestead);
    protocolSchedule.putMilestone(5, MainnetProtocolSpecs.daoRecoveryInit(protocolSchedule));
    protocolSchedule.putMilestone(6, MainnetProtocolSpecs.daoRecoveryTransition(protocolSchedule));
    protocolSchedule.putMilestone(15, homestead);
    return protocolSchedule;
  }

  private static ProtocolSchedule<Void> eip158ToByzantiumAt5() {
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(
        0, MainnetProtocolSpecs.spuriousDragon(CHAIN_ID, protocolSchedule));
    protocolSchedule.putMilestone(5, MainnetProtocolSpecs.byzantium(CHAIN_ID, protocolSchedule));
    return protocolSchedule;
  }
}
