/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.config;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.util.NetworkUtility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RlpxConfiguration {
  public static final float DEFAULT_FRACTION_REMOTE_CONNECTIONS_ALLOWED = 0.6f;
  private String clientId = "TestClient/1.0.0";
  private String bindHost = NetworkUtility.INADDR_ANY;
  private int bindPort = 30303;
  private List<SubProtocol> supportedProtocols = Collections.emptyList();

  public static RlpxConfiguration create() {
    return new RlpxConfiguration();
  }

  public RlpxConfiguration setSupportedProtocols(final SubProtocol... supportedProtocols) {
    this.supportedProtocols = Arrays.asList(supportedProtocols);
    return this;
  }

  public RlpxConfiguration setSupportedProtocols(final List<SubProtocol> supportedProtocols) {
    this.supportedProtocols = supportedProtocols;
    return this;
  }

  public List<SubProtocol> getSupportedProtocols() {
    return supportedProtocols;
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

  public String getClientId() {
    return clientId;
  }

  public RlpxConfiguration setClientId(final String clientId) {
    this.clientId = clientId;
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
    final RlpxConfiguration that = (RlpxConfiguration) o;
    return bindPort == that.bindPort && Objects.equals(bindHost, that.bindHost);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bindHost, bindPort);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("RlpxConfiguration{");
    sb.append("bindHost='").append(bindHost).append('\'');
    sb.append(", bindPort=").append(bindPort);
    sb.append('}');
    return sb.toString();
  }
}
