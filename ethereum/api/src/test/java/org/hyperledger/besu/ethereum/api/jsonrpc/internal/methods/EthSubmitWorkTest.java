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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthSubmitWorkTest {

  private EthSubmitWork method;
  private final String ETH_METHOD = "eth_submitWork";
  private final String hexValue =
      "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

  @Mock private PoWMiningCoordinator miningCoordinator;

  @BeforeEach
  public void setUp() {
    method = new EthSubmitWork(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldFailIfNoMiningEnabled() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse actualResponse = method.response(request);
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.NO_MINING_WORK_FOUND);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldFailIfMissingArguments() {
    final JsonRpcRequestContext request = requestWithParams();
    final PoWSolverInputs values =
        new PoWSolverInputs(UInt256.fromHexString(hexValue), Bytes.fromHexString(hexValue), 0);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.of(values));
    assertThatThrownBy(
            () -> method.response(request), "Missing required json rpc parameter at index 0")
        .isInstanceOf(InvalidJsonRpcParameters.class);
  }

  @Test
  public void shouldReturnTrueIfGivenCorrectResult() {
    final PoWSolverInputs firstInputs =
        new PoWSolverInputs(
            UInt256.fromHexString(
                "0x0083126e978d4fdf3b645a1cac083126e978d4fdf3b645a1cac083126e978d4f"),
            Bytes.wrap(
                new byte[] {
                  15, -114, -104, 87, -95, -36, -17, 120, 52, 1, 124, 61, -6, -66, 78, -27, -57,
                  118, -18, -64, -103, -91, -74, -121, 42, 91, -14, -98, 101, 86, -43, -51
                }),
            468);

    final PoWSolution expectedFirstOutput =
        new PoWSolution(
            -6506032554016940193L,
            Hash.fromHexString(
                "0xc5e3c33c86d64d0641dd3c86e8ce4628fe0aac0ef7b4c087c5fcaa45d5046d90"),
            null,
            firstInputs.getPrePowHash());
    final JsonRpcRequestContext request =
        requestWithParams(
            Bytes.ofUnsignedLong(expectedFirstOutput.getNonce()).trimLeadingZeros().toHexString(),
            Bytes.wrap(expectedFirstOutput.getPowHash()).toHexString(),
            expectedFirstOutput.getMixHash().toHexString());
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), true);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.of(firstInputs));
    // potentially could use a real miner here.
    when(miningCoordinator.submitWork(expectedFirstOutput)).thenReturn(true);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorOnNoneMiningNode() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.NO_MINING_WORK_FOUND);
    when(miningCoordinator.getWorkDefinition()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", ETH_METHOD, params));
  }
}
