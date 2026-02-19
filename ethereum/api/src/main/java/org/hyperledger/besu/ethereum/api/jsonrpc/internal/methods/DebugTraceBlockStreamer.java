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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLog;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.debug.TracerType;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class DebugTraceBlockStreamer {

  private final Block block;
  private final TraceOptions traceOptions;
  private final ProtocolSchedule protocolSchedule;
  private final BlockchainQueries blockchainQueries;
  private final StringBuilder hexBuf = new StringBuilder(66);

  public DebugTraceBlockStreamer(
      final Block block,
      final TraceOptions traceOptions,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries) {
    this.block = block;
    this.traceOptions = traceOptions;
    this.protocolSchedule = protocolSchedule;
    this.blockchainQueries = blockchainQueries;
  }

  public void streamTo(final OutputStream out, final ObjectMapper mapper) throws IOException {
    final JsonGenerator gen = mapper.getFactory().createGenerator(out);
    gen.setCodec(mapper);
    gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    gen.disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);

    gen.writeStartArray();
    Tracer.processTracing(
        blockchainQueries,
        Optional.of(block.getHeader()),
        traceableState -> {
          final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(block.getHeader());
          final MainnetTransactionProcessor transactionProcessor =
              protocolSpec.getTransactionProcessor();
          final TraceBlock.ChainUpdater chainUpdater = new TraceBlock.ChainUpdater(traceableState);

          final BlockHeader header = block.getHeader();
          final Optional<BlockHeader> maybeParentHeader =
              blockchainQueries.getBlockchain().getBlockHeader(header.getParentHash());
          final Wei blobGasPrice =
              protocolSpec
                  .getFeeMarket()
                  .blobGasPricePerGas(
                      maybeParentHeader
                          .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
                          .orElse(BlobGas.ZERO));
          final BlockHashLookup blockHashLookup =
              protocolSpec
                  .getPreExecutionProcessor()
                  .createBlockHashLookup(blockchainQueries.getBlockchain(), header);

          final boolean isOpcodeTracer = traceOptions.tracerType() == TracerType.OPCODE_TRACER;

          for (final Transaction transaction : block.getBody().getTransactions()) {
            if (isOpcodeTracer) {
              streamOpcodeTransaction(
                  gen,
                  transaction,
                  chainUpdater,
                  transactionProcessor,
                  header,
                  blobGasPrice,
                  blockHashLookup);
            } else {
              accumulateTransaction(
                  gen,
                  transaction,
                  chainUpdater,
                  transactionProcessor,
                  protocolSpec,
                  header,
                  blobGasPrice,
                  blockHashLookup);
            }
          }

          return Optional.of(Boolean.TRUE);
        });
    gen.writeEndArray();
    gen.flush();
  }

  private void streamOpcodeTransaction(
      final JsonGenerator gen,
      final Transaction transaction,
      final TraceBlock.ChainUpdater chainUpdater,
      final MainnetTransactionProcessor transactionProcessor,
      final BlockHeader header,
      final Wei blobGasPrice,
      final BlockHashLookup blockHashLookup) {
    final DebugOperationTracer tracer =
        new DebugOperationTracer(traceOptions.opCodeTracerConfig(), true);

    try {
      gen.writeStartObject();
      gen.writeStringField("txHash", transaction.getHash().toHexString());
      gen.writeFieldName("result");
      gen.writeStartObject();

      // structLogs written during execution (before gas/failed/returnValue are known)
      gen.writeFieldName("structLogs");
      gen.writeStartArray();

      tracer.setStreamingWriter(
          (pc, opcode, gasRemaining, gasCost, depth, stack, frame, halt, revert) ->
              writeStreamingStructLog(
                  gen, pc, opcode, gasRemaining, gasCost, depth, stack, frame, halt, revert));

      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              chainUpdater.getNextUpdater(),
              header,
              transaction,
              header.getCoinbase(),
              tracer,
              blockHashLookup,
              ImmutableTransactionValidationParams.builder().build(),
              blobGasPrice,
              Optional.empty());

      gen.writeEndArray(); // end structLogs

      final long gas = transaction.getGasLimit() - result.getGasRemaining();
      gen.writeNumberField("gas", gas);
      gen.writeBooleanField("failed", !result.isSuccessful());
      gen.writeStringField("returnValue", result.getOutput().toUnprefixedHexString());

      gen.writeEndObject(); // end result
      gen.writeEndObject(); // end tx entry
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void accumulateTransaction(
      final JsonGenerator gen,
      final Transaction transaction,
      final TraceBlock.ChainUpdater chainUpdater,
      final MainnetTransactionProcessor transactionProcessor,
      final ProtocolSpec protocolSpec,
      final BlockHeader header,
      final Wei blobGasPrice,
      final BlockHashLookup blockHashLookup) {
    final DebugOperationTracer tracer =
        new DebugOperationTracer(traceOptions.opCodeTracerConfig(), true);

    final AccessLocationTracker accessListTracker =
        BlockAccessList.BlockAccessListBuilder.createTransactionAccessLocationTracker(0);

    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            chainUpdater.getNextUpdater(),
            header,
            transaction,
            header.getCoinbase(),
            tracer,
            blockHashLookup,
            ImmutableTransactionValidationParams.builder().build(),
            blobGasPrice,
            Optional.of(accessListTracker));

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            transaction,
            result,
            tracer.copyTraceFrames(),
            Optional.empty(),
            accessListTracker.getTouchedAccounts());
    tracer.reset();

    final DebugTraceTransactionResult traceResult =
        DebugTraceTransactionStepFactory.create(traceOptions, protocolSpec).apply(transactionTrace);

    try {
      gen.writeObject(traceResult);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeStreamingStructLog(
      final JsonGenerator gen,
      final int pc,
      final String opcode,
      final long gasRemaining,
      final long gasCost,
      final int depth,
      final Bytes[] stack,
      final MessageFrame frame,
      final ExceptionalHaltReason haltReason,
      final Bytes revertReason) {
    try {
      gen.writeStartObject();

      gen.writeNumberField("pc", pc);
      gen.writeStringField("op", opcode);
      gen.writeNumberField("gas", gasRemaining);
      gen.writeNumberField("gasCost", gasCost);
      gen.writeNumberField("depth", depth + 1);

      // stack - use pre-execution stack references directly (no copy)
      if (stack != null) {
        gen.writeFieldName("stack");
        gen.writeStartArray();
        for (final Bytes entry : stack) {
          StructLog.toCompactHex(entry, true, hexBuf);
          gen.writeString(hexBuf.toString());
        }
        gen.writeEndArray();
      }

      // memory - read directly from frame (mutable view, no copy)
      if (traceOptions.opCodeTracerConfig().traceMemory() && frame.memoryWordSize() > 0) {
        gen.writeFieldName("memory");
        gen.writeStartArray();
        final int wordCount = frame.memoryWordSize();
        for (int i = 0; i < wordCount; i++) {
          StructLog.toCompactHex(frame.readMutableMemory(i * 32L, 32), true, hexBuf);
          gen.writeString(hexBuf.toString());
        }
        gen.writeEndArray();
      }

      // storage - read directly from world updater (no intermediate copy)
      if (traceOptions.opCodeTracerConfig().traceStorage()) {
        try {
          final MutableAccount account =
              frame.getWorldUpdater().getAccount(frame.getRecipientAddress());
          if (account != null) {
            final Map<UInt256, UInt256> updatedStorage = account.getUpdatedStorage();
            if (!updatedStorage.isEmpty()) {
              gen.writeFieldName("storage");
              gen.writeStartObject();
              for (final Map.Entry<UInt256, UInt256> entry :
                  new TreeMap<>(updatedStorage).entrySet()) {
                StructLog.toCompactHex(entry.getKey(), false, hexBuf);
                final String key = hexBuf.toString();
                StructLog.toCompactHex(entry.getValue(), false, hexBuf);
                gen.writeStringField(key, hexBuf.toString());
              }
              gen.writeEndObject();
            }
          }
        } catch (final ModificationNotAllowedException e) {
          // no storage to write
        }
      }

      // reason (revert reason)
      if (revertReason != null) {
        StructLog.toCompactHex(revertReason, true, hexBuf);
        gen.writeStringField("reason", hexBuf.toString());
      }

      // error
      if (haltReason != null) {
        gen.writeFieldName("error");
        gen.writeStartArray();
        gen.writeString(haltReason.name());
        gen.writeEndArray();
      }

      gen.writeEndObject();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
