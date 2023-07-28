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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.Collection;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

public class DebugTraceBlockByNumberTest {

  private final BlockchainQueries blockchainQueries =
      mock(BlockchainQueries.class, Answers.RETURNS_DEEP_STUBS);

  private final Tracer.TraceableState worldState = mock(Tracer.TraceableState.class);
  private final BlockTracer blockTracer = mock(BlockTracer.class, Answers.RETURNS_DEEP_STUBS);
  private final DebugTraceBlockByNumber debugTraceBlockByNumber =
      new DebugTraceBlockByNumber(() -> blockTracer, blockchainQueries);

  private final Hash blockHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

  @Test
  public void nameShouldBeDebugTraceBlockByNumber() {
    assertThat(debugTraceBlockByNumber.getName()).isEqualTo("debug_traceBlockByNumber");
  }

  @Test
  public void shouldReturnCorrectResponse() {
    final long blockNumber = 1L;
    final Object[] params = new Object[] {Long.toHexString(blockNumber)};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlockByNumber", params));

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

    final BlockTrace blockTrace = new BlockTrace(asList(transaction1Trace, transaction2Trace));

    when(transaction1Trace.getTraceFrames()).thenReturn(singletonList(traceFrame));
    when(transaction2Trace.getTraceFrames()).thenReturn(singletonList(traceFrame));
    when(transaction1Trace.getResult()).thenReturn(transaction1Result);
    when(transaction2Trace.getResult()).thenReturn(transaction2Result);
    when(transaction1Result.getOutput()).thenReturn(Bytes.fromHexString("1234"));
    when(transaction2Result.getOutput()).thenReturn(Bytes.fromHexString("1234"));
    when(blockchainQueries.getBlockHashByNumber(blockNumber)).thenReturn(Optional.of(blockHash));
    when(blockchainQueries.getBlockHeaderByHash(any(Hash.class)))
        .thenReturn(Optional.of(mock(BlockHeader.class, Answers.RETURNS_DEEP_STUBS)));

    doAnswer(
            invocation ->
                invocation
                    .<Function<MutableWorldState, ? extends Optional<? extends JsonRpcResponse>>>
                        getArgument(1)
                    .apply(worldState))
        .when(blockchainQueries)
        .getAndMapWorldState(any(), any());
    when(blockTracer.trace(any(Tracer.TraceableState.class), any(Hash.class), any()))
        .thenReturn(Optional.of(blockTrace));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) debugTraceBlockByNumber.response(request);
    final Collection<DebugTraceTransactionResult> result = getResult(response);
    assertThat(result)
        .usingFieldByFieldElementComparator()
        .isEqualTo(DebugTraceTransactionResult.of(blockTrace.getTransactionTraces()));
  }

  @SuppressWarnings("unchecked")
  private Collection<DebugTraceTransactionResult> getResult(final JsonRpcSuccessResponse response) {
    return (Collection<DebugTraceTransactionResult>) response.getResult();
  }
}
