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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.jupiter.api.Test;

public class NetworkResultTest {

  @Test
  public void localAndRemoteAddressShouldNotStartWithForwardSlash() {
    final SocketAddress socketAddress = new InetSocketAddress("1.2.3.4", 7890);
    final NetworkResult networkResult = new NetworkResult(socketAddress, socketAddress, true);

    assertThat(networkResult.getLocalAddress()).isEqualTo("1.2.3.4:7890");
    assertThat(networkResult.getRemoteAddress()).isEqualTo("1.2.3.4:7890");
    assertThat(networkResult.isInbound()).isTrue();
  }

  @Test
  public void inboundFieldShouldReflectConnectionDirection() {
    final SocketAddress localAddress = new InetSocketAddress("192.168.0.1", 30303);
    final SocketAddress remoteAddress = new InetSocketAddress("10.0.0.1", 30303);

    final NetworkResult inboundConnection = new NetworkResult(localAddress, remoteAddress, true);
    assertThat(inboundConnection.isInbound()).isTrue();

    final NetworkResult outboundConnection = new NetworkResult(localAddress, remoteAddress, false);
    assertThat(outboundConnection.isInbound()).isFalse();
  }
}
