package tech.pegasys.pantheon.ethereum.p2p.config;

import java.util.Objects;

public class RlpxConfiguration {
  private String clientId = "TestClient/1.0.0";
  private String bindHost = "0.0.0.0";
  private int bindPort = 30303;
  private int maxPeers = 25;
  private WireProtocolConfig wire = new WireProtocolConfig();

  public static RlpxConfiguration create() {
    return new RlpxConfiguration();
  }

  public String getBindHost() {
    return bindHost;
  }

  public RlpxConfiguration setBindHost(final String bindHost) {
    this.bindHost = bindHost;
    return this;
  }

  public int getBindPort() {
    return bindPort;
  }

  public RlpxConfiguration setBindPort(final int bindPort) {
    this.bindPort = bindPort;
    return this;
  }

  public WireProtocolConfig getWire() {
    return wire;
  }

  public RlpxConfiguration setWire(final WireProtocolConfig wire) {
    this.wire = wire;
    return this;
  }

  public RlpxConfiguration setMaxPeers(final int peers) {
    maxPeers = peers;
    return this;
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RlpxConfiguration that = (RlpxConfiguration) o;
    return bindPort == that.bindPort
        && Objects.equals(bindHost, that.bindHost)
        && Objects.equals(wire, that.wire);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bindHost, bindPort, wire);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("RlpxConfiguration{");
    sb.append("bindHost='").append(bindHost).append('\'');
    sb.append(", bindPort=").append(bindPort);
    sb.append(", wire=").append(wire);
    sb.append('}');
    return sb.toString();
  }
}
