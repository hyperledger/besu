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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class EthProtocolVersionTest {
  private EthProtocolVersion method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_protocolVersion";
  private Set<Capability> supportedCapabilities;

  @Test
  public void returnsCorrectMethodName() {
    setupSupportedEthProtocols();
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturn63WhenMaxProtocolIsETH63() {

    setupSupportedEthProtocols();

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x3f");
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnNullNoEthProtocolsSupported() {

    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new EthProtocolVersion(supportedCapabilities);

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), null);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturn63WhenMixedProtocolsSupported() {

    setupSupportedEthProtocols();
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new EthProtocolVersion(supportedCapabilities);

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x3f");
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }

  private void setupSupportedEthProtocols() {
    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    method = new EthProtocolVersion(supportedCapabilities);
  }
}
