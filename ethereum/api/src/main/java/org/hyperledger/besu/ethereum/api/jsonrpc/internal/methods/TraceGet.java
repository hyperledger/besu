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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.MixInIgnoreRevertReason;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TraceGet extends AbstractTraceByHash implements JsonRpcMethod {
  private static final ObjectMapper MAPPER_IGNORE_REVERT_REASON = new ObjectMapper();

  public TraceGet(
      final Supplier<BlockTracer> blockTracerSupplier,
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule) {
    super(blockTracerSupplier, blockchainQueries, protocolSchedule);

    // The trace_get specification does not output the revert reason, so we have to remove it
    MAPPER_IGNORE_REVERT_REASON.addMixIn(FlatTrace.class, MixInIgnoreRevertReason.class);
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_GET.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash transactionHash = requestContext.getRequiredParameter(0, Hash.class);
    final List<?> traceNumbersAsStrings = requestContext.getRequiredParameter(1, List.class);
    final List<Integer> traceNumbers =
        traceNumbersAsStrings.stream()
            .map(t -> Integer.parseInt(((String) t).substring(2), 16))
            .collect(Collectors.toList());

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        MAPPER_IGNORE_REVERT_REASON.valueToTree(
            resultByTransactionHash(transactionHash)
                .filter(trace -> trace.getTraceAddress().equals(traceNumbers))
                .findFirst()
                .orElse(null)));
  }
}
