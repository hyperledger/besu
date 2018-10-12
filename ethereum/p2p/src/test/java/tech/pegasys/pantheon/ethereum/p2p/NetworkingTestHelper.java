package tech.pegasys.pantheon.ethereum.p2p;

import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;

public class NetworkingTestHelper {

  public static NetworkingConfiguration configWithRandomPorts() {
    return NetworkingConfiguration.create()
        .setRlpx(RlpxConfiguration.create().setBindPort(0))
        .setDiscovery(DiscoveryConfiguration.create().setBindPort(0));
  }
}
