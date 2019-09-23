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

import org.junit.Test;

public class NetworkResultTest {

  @Test
  public void localAndRemoteAddressShouldNotStartWithForwardSlash() {
    final SocketAddress socketAddress = new InetSocketAddress("1.2.3.4", 7890);
    final NetworkResult networkResult = new NetworkResult(socketAddress, socketAddress);

    assertThat(networkResult.getLocalAddress()).isEqualTo("1.2.3.4:7890");
    assertThat(networkResult.getRemoteAddress()).isEqualTo("1.2.3.4:7890");
  }
}
