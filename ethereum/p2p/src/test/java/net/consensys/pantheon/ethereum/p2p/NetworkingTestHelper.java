package net.consensys.pantheon.ethereum.p2p;

import net.consensys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.RlpxConfiguration;

public class NetworkingTestHelper {

  public static NetworkingConfiguration configWithRandomPorts() {
    return NetworkingConfiguration.create()
        .setRlpx(RlpxConfiguration.create().setBindPort(0))
        .setDiscovery(DiscoveryConfiguration.create().setBindPort(0));
  }
}
