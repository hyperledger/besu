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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class DebugTraceBlockByHashTest {

  private final BlockTracer blockTracer = mock(BlockTracer.class);
  private final DebugTraceBlockByHash debugTraceBlockByHash =
      new DebugTraceBlockByHash(() -> blockTracer);

  private final Hash blockHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

  @Test
  public void nameShouldBeDebugTraceBlockByHash() {
    assertThat(debugTraceBlockByHash.getName()).isEqualTo("debug_traceBlockByHash");
  }

  @Test
  public void shouldReturnCorrectResponse() {
    final Object[] params = new Object[] {blockHash};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlockByHash", params));

    final TraceFrame traceFrame =
        new TraceFrame(
            12,
            Optional.of("NONE"),
            45L,
            OptionalLong.of(56L),
            0L,
            2,
            Optional.empty(),
            null,
            Wei.ZERO,
            Bytes.EMPTY,
            Bytes.EMPTY,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            Optional.empty(),
            false,
            Optional.empty(),
            Optional.empty());

    final TransactionProcessingResult transaction1Result = mock(TransactionProcessingResult.class);
    final TransactionProcessingResult transaction2Result = mock(TransactionProcessingResult.class);

    final TransactionTrace transaction1Trace = mock(TransactionTrace.class);
    final TransactionTrace transaction2Trace = mock(TransactionTrace.class);

    BlockTrace blockTrace = new BlockTrace(Arrays.asList(transaction1Trace, transaction2Trace));

    when(transaction1Trace.getTraceFrames()).thenReturn(Arrays.asList(traceFrame));
    when(transaction2Trace.getTraceFrames()).thenReturn(Arrays.asList(traceFrame));
    when(transaction1Trace.getResult()).thenReturn(transaction1Result);
    when(transaction2Trace.getResult()).thenReturn(transaction2Result);
    when(transaction1Result.getOutput()).thenReturn(Bytes.fromHexString("1234"));
    when(transaction2Result.getOutput()).thenReturn(Bytes.fromHexString("1234"));
    when(blockTracer.trace(eq(blockHash), any())).thenReturn(Optional.of(blockTrace));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) debugTraceBlockByHash.response(request);
    final Collection<?> result = (Collection<?>) response.getResult();
    assertThat(result).hasSize(2);
  }
}
