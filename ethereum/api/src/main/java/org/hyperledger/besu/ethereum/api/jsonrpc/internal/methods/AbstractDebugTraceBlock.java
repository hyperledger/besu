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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.StreamingJsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLog;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLogWithError;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.TraceFrame;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public abstract class AbstractDebugTraceBlock implements JsonRpcMethod {

  private final ProtocolSchedule protocolSchedule;
  private final Supplier<BlockchainQueries> blockchainQueriesSupplier;

  public AbstractDebugTraceBlock(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final ObservableMetricsSystem metricsSystem,
      final EthScheduler ethScheduler) {
    this.blockchainQueriesSupplier = Suppliers.ofInstance(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
  }

  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueriesSupplier.get();
  }

  protected TraceOptions getTraceOptions(final JsonRpcRequestContext requestContext) {
    final TraceOptions traceOptions;
    try {
      traceOptions =
          requestContext
              .getOptionalParameter(1, TransactionTraceParams.class)
              .map(TransactionTraceParams::traceOptions)
              .orElse(TraceOptions.DEFAULT);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid transaction trace parameter (index 1)",
          RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS,
          e);
    } catch (IllegalArgumentException e) {
      // Handle invalid tracer type from TracerType.fromString()
      throw new InvalidJsonRpcParameters(
          e.getMessage(), RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS, e);
    }
    return traceOptions;
  }

  /**
   * Creates a streaming response that writes each transaction trace directly to the JSON output.
   * Struct logs within each transaction are streamed frame-by-frame to avoid accumulating all
   * trace data in memory. Peak memory = O(one_frame) instead of O(all_frames_in_block).
   */
  protected StreamingJsonRpcSuccessResponse.ResultWriter getStreamingTraces(
      final TraceOptions traceOptions, final Optional<Block> maybeBlock) {
    return generator -> {
      if (maybeBlock.isEmpty()) {
        generator.writeNull();
        return;
      }
      final Block block = maybeBlock.get();

      final Optional<?> tracingResult = Tracer.processTracing(
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
                            .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
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

                // Stream the tx trace: write struct logs frame-by-frame, then discard
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

                // Discard all trace frames immediately — this is the key memory optimization
                tracer.reset();
              }

              generator.writeEndArray();
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            return Optional.of(Boolean.TRUE);
          });

      if (tracingResult.isEmpty()) {
        generator.writeNull();
      }
    };
  }
}
