package net.consensys.pantheon.ethereum.p2p.config;

import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NetworkingConfiguration {
  private List<SubProtocol> supportedProtocols = Collections.emptyList();
  private DiscoveryConfiguration discovery = new DiscoveryConfiguration();
  private RlpxConfiguration rlpx = new RlpxConfiguration();
  private String clientId = "";

  public static NetworkingConfiguration create() {
    return new NetworkingConfiguration();
  }

  public List<SubProtocol> getSupportedProtocols() {
    return supportedProtocols;
  }

  public NetworkingConfiguration setSupportedProtocols(final List<SubProtocol> supportedProtocols) {
    this.supportedProtocols = supportedProtocols;
    return this;
  }

  public NetworkingConfiguration setSupportedProtocols(final SubProtocol... supportedProtocols) {
    this.supportedProtocols = Arrays.asList(supportedProtocols);
    return this;
  }

  public DiscoveryConfiguration getDiscovery() {
    return discovery;
  }

  public NetworkingConfiguration setDiscovery(final DiscoveryConfiguration discovery) {
    this.discovery = discovery;
    return this;
  }

  public RlpxConfiguration getRlpx() {
    return rlpx;
  }

  public NetworkingConfiguration setRlpx(final RlpxConfiguration rlpx) {
    this.rlpx = rlpx;
    return this;
  }

  public String getClientId() {
    return clientId;
  }

  public NetworkingConfiguration setClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof NetworkingConfiguration)) {
      return false;
    }
    final NetworkingConfiguration that = (NetworkingConfiguration) o;
    return Objects.equals(discovery, that.discovery) && Objects.equals(rlpx, that.rlpx);
  }

  @Override
  public int hashCode() {
    return Objects.hash(discovery, rlpx);
  }

  @Override
  public String toString() {
    return "NetworkingConfiguration{" + "discovery=" + discovery + ", rlpx=" + rlpx + '}';
  }
}
