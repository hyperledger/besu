package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.plugin.services.p2p.P2PService;

public class P2PServiceImpl implements P2PService {

  private final P2PNetwork p2PNetwork;

  public P2PServiceImpl(final P2PNetwork p2PNetwork) {
    this.p2PNetwork = p2PNetwork;
  }

  @Override
  public void enableDiscovery() {
    p2PNetwork.start();
  }

  @Override
  public void disableDiscovery() {
    p2PNetwork.stop();
  }
}
