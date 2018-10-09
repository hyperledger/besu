package net.consensys.pantheon.ethereum.p2p.config;

import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.ArrayList;
import java.util.List;

public class SubProtocolConfiguration {

  private final List<SubProtocol> subProtocols = new ArrayList<>();
  private final List<ProtocolManager> protocolManagers = new ArrayList<>();

  public SubProtocolConfiguration withSubProtocol(
      final SubProtocol subProtocol, final ProtocolManager protocolManager) {
    subProtocols.add(subProtocol);
    protocolManagers.add(protocolManager);
    return this;
  }

  public List<SubProtocol> getSubProtocols() {
    return subProtocols;
  }

  public List<ProtocolManager> getProtocolManagers() {
    return protocolManagers;
  }
}
