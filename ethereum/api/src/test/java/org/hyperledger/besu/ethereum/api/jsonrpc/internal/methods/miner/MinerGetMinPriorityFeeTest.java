/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MinerGetMinPriorityFeeTest {
  MiningConfiguration miningConfiguration = mock(MiningConfiguration.class);
  private MinerGetMinPriorityFee method;

  @BeforeEach
  public void setUp() {
    method = new MinerGetMinPriorityFee(miningConfiguration);
  }

  @Test
  public void shouldReturnMinPriorityFee() {
    String minPriorityFee = "0x46";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", method.getName(), new Object[] {}));

    when(miningConfiguration.getMinPriorityFeePerGas())
        .thenReturn(Wei.fromHexString(minPriorityFee));

    final JsonRpcResponse expected =
        new JsonRpcSuccessResponse(request.getRequest().getId(), minPriorityFee);

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
}
