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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor;

import static java.util.function.Predicate.isEqual;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import org.apache.tuweni.units.bigints.UInt256;

/** Used to produce debug traces of transactions */
public class TransactionTracer {

  public static final String TRACE_PATH = "traces";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final BlockReplay blockReplay;

  public TransactionTracer(final BlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<TransactionTrace> traceTransaction(
      final Hash blockHash, final Hash transactionHash, final DebugOperationTracer tracer) {
    return blockReplay.beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, header, blockchain, worldState, transactionProcessor) -> {
          final TransactionProcessingResult result =
              processTransaction(
                  header,
                  blockchain,
                  worldState.updater(),
                  transaction,
                  transactionProcessor,
                  tracer);
          return new TransactionTrace(transaction, result, tracer.getTraceFrames());
        });
  }

  public List<String> traceTransactionToFile(
      final Hash blockHash,
      final Optional<TransactionTraceParams> transactionTraceParams,
      final Path traceDir) {

    final Optional<Hash> selectedHash =
        transactionTraceParams
            .map(TransactionTraceParams::getTransactionHash)
            .map(Hash::fromHexString);
    final boolean showMemory =
        transactionTraceParams
            .map(TransactionTraceParams::traceOptions)
            .map(TraceOptions::isMemoryEnabled)
            .orElse(true);

    if (!Files.isDirectory(traceDir) && !traceDir.toFile().mkdirs()) {
      throw new RuntimeException(
          String.format("Trace directory '%s' does not exist and could not be made.", traceDir));
    }

    return blockReplay
        .performActionWithBlock(
            blockHash,
            (body, header, blockchain, worldState, transactionProcessor) -> {
              WorldUpdater stackedUpdater = worldState.updater().updater();
              final List<String> traces = new ArrayList<>();
              for (int i = 0; i < body.getTransactions().size(); i++) {
                ((StackedUpdater<?, ?>) stackedUpdater).markTransactionBoundary();
                final Transaction transaction = body.getTransactions().get(i);
                if (selectedHash.isEmpty()
                    || selectedHash.filter(isEqual(transaction.getHash())).isPresent()) {
                  final File traceFile = generateTraceFile(traceDir, blockHash, i, transaction);
                  try (PrintStream out = new PrintStream(new FileOutputStream(traceFile))) {
                    final Stopwatch timer = Stopwatch.createStarted();
                    final TransactionProcessingResult result =
                        processTransaction(
                            header,
                            blockchain,
                            stackedUpdater,
                            transaction,
                            transactionProcessor,
                            new StandardJsonTracer(out, showMemory));
                    out.println(
                        summaryTrace(
                            transaction, timer.stop().elapsed(TimeUnit.NANOSECONDS), result));
                    traces.add(traceFile.getPath());
                  } catch (FileNotFoundException e) {
                    throw new RuntimeException(
                        "Unable to create transaction trace : " + e.getMessage());
                  }
                } else {
                  processTransaction(
                      header,
                      blockchain,
                      stackedUpdater,
                      transaction,
                      transactionProcessor,
                      OperationTracer.NO_TRACING);
                }
              }
              return Optional.of(traces);
            })
        .orElse(new ArrayList<>());
  }

  private File generateTraceFile(
      final Path traceDir,
      final Hash blockHash,
      final int indexTransaction,
      final Transaction transaction) {
    return traceDir
        .resolve(
            String.format(
                "block_%.10s-%d-%.10s-%s",
                blockHash.toHexString(),
                indexTransaction,
                transaction.getHash().toHexString(),
                System.currentTimeMillis()))
        .toFile();
  }

  private TransactionProcessingResult processTransaction(
      final BlockHeader header,
      final Blockchain blockchain,
      final WorldUpdater worldUpdater,
      final Transaction transaction,
      final MainnetTransactionProcessor transactionProcessor,
      final OperationTracer tracer) {
    return transactionProcessor.processTransaction(
        blockchain,
        worldUpdater,
        header,
        transaction,
        header.getCoinbase(),
        tracer,
        new BlockHashLookup(header, blockchain),
        false,
        ImmutableTransactionValidationParams.builder().isAllowFutureNonce(true).build());
  }

  public static String summaryTrace(
      final Transaction transaction, final long timer, final TransactionProcessingResult result) {
    final ObjectNode summaryLine = OBJECT_MAPPER.createObjectNode();
    summaryLine.put("output", result.getOutput().toUnprefixedHexString());
    summaryLine.put(
        "gasUsed",
        StandardJsonTracer.shortNumber(
            UInt256.valueOf(transaction.getGasLimit() - result.getGasRemaining())));
    summaryLine.put("time", timer);
    return summaryLine.toString();
  }
}
