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
package org.hyperledger.besu.ethereum.api.util;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class TraceUtil {

  private TraceUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static Stream<FlatTrace> resultByTransactionHash(
      final Hash transactionHash,
      final BlockchainQueries blockchainQueries,
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule) {
    return blockchainQueries
        .transactionByHash(transactionHash)
        .flatMap(TransactionWithMetadata::getBlockNumber)
        .flatMap(blockNumber -> blockchainQueries.getBlockchain().getBlockByNumber(blockNumber))
        .map(block -> getTraceBlock(block, transactionHash, blockTracerSupplier, protocolSchedule))
        .orElse(Stream.empty());
  }

  private static Stream<FlatTrace> getTraceBlock(
      final Block block,
      final Hash transactionHash,
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule) {
    if (block == null || block.getBody().getTransactions().isEmpty()) {
      return Stream.empty();
    }
    final TransactionTrace transactionTrace =
        getTransactionTrace(block, transactionHash, blockTracerSupplier);
    return getTraceStream(protocolSchedule, transactionTrace, block);
  }

  private static TransactionTrace getTransactionTrace(
      final Block block,
      final Hash transactionHash,
      final Supplier<BlockTracer> blockTracerSupplier) {
    return blockTracerSupplier
        .get()
        .trace(block, new DebugOperationTracer(new TraceOptions(false, false, true)))
        .map(BlockTrace::getTransactionTraces)
        .orElse(Collections.emptyList())
        .stream()
        .filter(trxTrace -> trxTrace.getTransaction().getHash().equals(transactionHash))
        .findFirst()
        .orElseThrow();
  }

  private static Stream<FlatTrace> getTraceStream(
      final ProtocolSchedule protocolSchedule,
      final TransactionTrace transactionTrace,
      final Block block) {
    return FlatTraceGenerator.generateFromTransactionTraceAndBlock(
            protocolSchedule, transactionTrace, block)
        .map(FlatTrace.class::cast);
  }

  public static JsonNode arrayNodeFromTraceStream(final Stream<FlatTrace> traceStream) {
    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNode resultArrayNode = mapper.createArrayNode();
    traceStream.forEachOrdered(resultArrayNode::addPOJO);
    return resultArrayNode;
  }
}
