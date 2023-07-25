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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MinerStopTest {

  private MinerStop method;

  @Mock private PoWMiningCoordinator miningCoordinator;

  @BeforeEach
  public void before() {
    method = new MinerStop(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("miner_stop");
  }

  @Test
  public void shouldReturnTrueWhenMiningStopsSuccessfully() {
    when(miningCoordinator.disable()).thenReturn(true);
    final JsonRpcRequestContext request = minerStop();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnFalseWhenMiningDoesNotStopSuccessfully() {
    when(miningCoordinator.disable()).thenReturn(false);
    final JsonRpcRequestContext request = minerStop();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, false);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext minerStop() {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "miner_stop", null));
  }
}
