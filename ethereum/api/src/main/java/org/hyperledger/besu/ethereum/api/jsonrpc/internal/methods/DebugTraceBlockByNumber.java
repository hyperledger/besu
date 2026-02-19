/*
 * Copyright contributors to Besu.
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

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.StreamingJsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLog;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLogWithError;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.TraceFrame;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

public class DebugTraceBlockByNumber extends AbstractBlockParameterMethod {

  private final ProtocolSchedule protocolSchedule;

  public DebugTraceBlockByNumber(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final ObservableMetricsSystem metricsSystem,
      final EthScheduler ethScheduler) {
    super(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_BLOCK_BY_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(0, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final BlockParameter blockParam = blockParameter(requestContext);
    final Optional<Long> blockNumber = blockParam.getNumber();
    final long resolvedBlockNumber;

    if (blockNumber.isPresent()) {
      resolvedBlockNumber = blockNumber.get();
    } else if (blockParam.isLatest()) {
      resolvedBlockNumber = getBlockchainQueries().headBlockNumber();
    } else if (blockParam.isFinalized()) {
      resolvedBlockNumber =
          getBlockchainQueries().finalizedBlockHeader().map(h -> h.getNumber()).orElse(-1L);
    } else if (blockParam.isSafe()) {
      resolvedBlockNumber =
          getBlockchainQueries().safeBlockHeader().map(h -> h.getNumber()).orElse(-1L);
    } else {
      resolvedBlockNumber = getBlockchainQueries().headBlockNumber();
    }

    final TraceOptions traceOptions;
    try {
      traceOptions =
          requestContext
              .getOptionalParameter(1, TransactionTraceParams.class)
              .map(TransactionTraceParams::traceOptions)
              .orElse(TraceOptions.DEFAULT);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid transaction trace parameter (index 1)",
          RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS,
          e);
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcParameters(
          e.getMessage(), RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS, e);
    }

    Optional<Block> maybeBlock =
        getBlockchainQueries().getBlockchain().getBlockByNumber(resolvedBlockNumber);

    StreamingJsonRpcSuccessResponse.ResultWriter resultWriter =
        generator -> {
          if (maybeBlock.isEmpty()) {
            generator.writeNull();
            return;
          }
          final Block block = maybeBlock.get();

          Tracer.processTracing(
              getBlockchainQueries(),
              Optional.of(block.getHeader()),
              traceableState -> {
                final ProtocolSpec protocolSpec =
                    protocolSchedule.getByBlockHeader(block.getHeader());
                final MainnetTransactionProcessor transactionProcessor =
                    protocolSpec.getTransactionProcessor();
                final TraceBlock.ChainUpdater chainUpdater =
                    new TraceBlock.ChainUpdater(traceableState);
                final BlockHeader header = block.getHeader();
                final Optional<BlockHeader> maybeParentHeader =
                    getBlockchainQueries().getBlockchain().getBlockHeader(header.getParentHash());
                final Wei blobGasPrice =
                    protocolSpec
                        .getFeeMarket()
                        .blobGasPricePerGas(
                            maybeParentHeader
                                .map(parent ->
                                    calculateExcessBlobGasForParent(protocolSpec, parent))
                                .orElse(BlobGas.ZERO));
                final BlockHashLookup blockHashLookup =
                    protocolSpec.getPreExecutionProcessor().createBlockHashLookup(
                        getBlockchainQueries().getBlockchain(), header);

                final DebugOperationTracer tracer =
                    new DebugOperationTracer(traceOptions.opCodeTracerConfig(), true);

                try {
                  generator.writeStartArray();

                  for (final Transaction tx : block.getBody().getTransactions()) {
                    final AccessLocationTracker accessListTracker =
                        BlockAccessList.BlockAccessListBuilder
                            .createTransactionAccessLocationTracker(0);
                    final TransactionProcessingResult result =
                        transactionProcessor.processTransaction(
                            chainUpdater.getNextUpdater(),
                            header,
                            tx,
                            header.getCoinbase(),
                            tracer,
                            blockHashLookup,
                            ImmutableTransactionValidationParams.builder().build(),
                            blobGasPrice,
                            Optional.of(accessListTracker));

                    final List<TraceFrame> frames = tracer.getTraceFrames();
                    final long gas = tx.getGasLimit() - result.getGasRemaining();
                    final String returnValue = result.getOutput().toString().substring(2);
                    final boolean failed = !result.isSuccessful();

                    generator.writeStartObject();
                    generator.writeStringField("txHash", tx.getHash().toHexString());
                    generator.writeFieldName("result");
                    generator.writeStartObject();
                    generator.writeNumberField("gas", gas);
                    generator.writeBooleanField("failed", failed);
                    generator.writeStringField("returnValue", returnValue);
                    generator.writeFieldName("structLogs");
                    generator.writeStartArray();
                    for (final TraceFrame frame : frames) {
                      final StructLog structLog = frame.getExceptionalHaltReason().isPresent()
                          ? new StructLogWithError(frame)
                          : new StructLog(frame);
                      generator.writeObject(structLog);
                    }
                    generator.writeEndArray();
                    generator.writeEndObject();
                    generator.writeEndObject();
                    generator.flush();

                    tracer.reset();
                  }

                  generator.writeEndArray();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
                return Optional.empty();
              });
        };

    return new StreamingJsonRpcSuccessResponse(
        requestContext.getRequest().getId(), resultWriter);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    // Not used — response() is overridden to use streaming
    throw new UnsupportedOperationException("Use response() directly");
  }
}
