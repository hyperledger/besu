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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
class TraceBlockTest {
  private final String methodName = "trace_block";
  private final BlockTracer blockTracer = mock(BlockTracer.class);
  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private final ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
  private final TraceBlock traceBlock =
      new TraceBlock(() -> blockTracer, protocolSchedule, blockchainQueries);

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(traceBlock.getName()).isEqualTo(methodName);
  }

  @Test
  public void shouldNotBlock() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", methodName, new Object[] {"0x01"}));

    when(blockchainQueries.getBlockchain())
        .thenAnswer(new AnswersWithDelay(6000, new Returns(null)));

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) traceBlock.response(request);
    assertThat(response.getError()).isEqualTo(JsonRpcError.TIMEOUT_ERROR);
  }
}
