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
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.mainnet.DirectAcyclicGraphSeed;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Optional;

import com.google.common.io.BaseEncoding;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetWorkTest {

  private EthGetWork method;
  private final String ETH_METHOD = "eth_getWork";
  private final String hexValue =
      "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

  @Mock private EthHashMiningCoordinator miningCoordinator;

  @Before
  public void setUp() {
    method = new EthGetWork(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnCorrectResultOnGenesisDAG() {
    final JsonRpcRequest request = requestWithParams();
    final EthHashSolverInputs values =
        new EthHashSolverInputs(
            UInt256.fromHexString(hexValue), BaseEncoding.base16().lowerCase().decode(hexValue), 0);
    final String[] expectedValue = {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0x0000000000000000000000000000000000000000000000000000000000000000",
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    };
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), expectedValue);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.of(values));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnCorrectResultOnHighBlockSeed() {
    final JsonRpcRequest request = requestWithParams();
    final EthHashSolverInputs values =
        new EthHashSolverInputs(
            UInt256.fromHexString(hexValue),
            BaseEncoding.base16().lowerCase().decode(hexValue),
            30000);
    final String[] expectedValue = {
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0x" + BaseEncoding.base16().lowerCase().encode(DirectAcyclicGraphSeed.dagSeed(30000)),
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    };
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), expectedValue);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.of(values));
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorOnNoneMiningNode() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NO_MINING_WORK_FOUND);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest("2.0", ETH_METHOD, params);
  }
}
