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

import java.net.SocketAddress;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"localAddress", "remoteAddress"})
public class NetworkResult {

  private final String localAddress;
  private final String remoteAddress;

  public NetworkResult(final SocketAddress localAddress, final SocketAddress remoteAddress) {
    this.localAddress = removeTrailingSlash(localAddress.toString());
    this.remoteAddress = removeTrailingSlash(remoteAddress.toString());
  }

  @JsonGetter(value = "localAddress")
  public String getLocalAddress() {
    return localAddress;
  }

  @JsonGetter(value = "remoteAddress")
  public String getRemoteAddress() {
    return remoteAddress;
  }

  private String removeTrailingSlash(final String address) {
    if (address != null && address.startsWith("/")) {
      return address.substring(1);
    } else {
      return address;
    }
  }
}
