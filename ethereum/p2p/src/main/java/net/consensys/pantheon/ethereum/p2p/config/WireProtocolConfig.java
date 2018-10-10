package net.consensys.pantheon.ethereum.p2p.config;

import java.util.Objects;

public class WireProtocolConfig {
  private long keepAlivePeriodMs = 15000;
  private long handshakeTimeoutMs = 5000;
  private int maxFailedKeepAlives = 3;

  public long getKeepAlivePeriodMs() {
    return keepAlivePeriodMs;
  }

  public WireProtocolConfig setKeepAlivePeriodMs(final long keepAlivePeriodMs) {
    this.keepAlivePeriodMs = keepAlivePeriodMs;
    return this;
  }

  public long getHandshakeTimeoutMs() {
    return handshakeTimeoutMs;
  }

  public WireProtocolConfig setHandshakeTimeoutMs(final long handshakeTimeoutMs) {
    this.handshakeTimeoutMs = handshakeTimeoutMs;
    return this;
  }

  public int getMaxFailedKeepAlives() {
    return maxFailedKeepAlives;
  }

  public WireProtocolConfig setMaxFailedKeepAlives(final int maxFailedKeepAlives) {
    this.maxFailedKeepAlives = maxFailedKeepAlives;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WireProtocolConfig that = (WireProtocolConfig) o;
    return keepAlivePeriodMs == that.keepAlivePeriodMs
        && handshakeTimeoutMs == that.handshakeTimeoutMs
        && maxFailedKeepAlives == that.maxFailedKeepAlives;
  }

  @Override
  public int hashCode() {
    return Objects.hash(keepAlivePeriodMs, handshakeTimeoutMs, maxFailedKeepAlives);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("WireProtocolConfig{");
    sb.append("keepAlivePeriodMs=").append(keepAlivePeriodMs);
    sb.append(", handshakeTimeoutMs=").append(handshakeTimeoutMs);
    sb.append(", maxFailedKeepAlives=").append(maxFailedKeepAlives);
    sb.append('}');
    return sb.toString();
  }
}
