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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerSetEtherbaseTest {

  private MinerSetEtherbase method;

  @Mock private MinerSetCoinbase minerSetCoinbase;

  @Before
  public void before() {
    this.method = new MinerSetEtherbase(minerSetCoinbase);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("miner_setEtherbase");
  }

  @Test
  public void shouldDelegateToMinerSetCoinbase() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(null, "miner_setEtherbase", new Object[] {"0x0"}));

    final ArgumentCaptor<JsonRpcRequestContext> requestCaptor =
        ArgumentCaptor.forClass(JsonRpcRequestContext.class);
    when(minerSetCoinbase.response(requestCaptor.capture()))
        .thenReturn(new JsonRpcSuccessResponse(null, true));

    method.response(request);

    final JsonRpcRequestContext delegatedRequest = requestCaptor.getValue();
    assertThat(delegatedRequest).usingRecursiveComparison().isEqualTo(request);
  }
}
