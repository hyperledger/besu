/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.config;

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
