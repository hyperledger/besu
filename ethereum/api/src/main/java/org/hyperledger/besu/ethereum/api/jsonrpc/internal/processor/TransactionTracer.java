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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor.Result;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.StandardJsonTracer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Used to produce debug traces of transactions */
public class TransactionTracer {

  private static final Logger LOG = LogManager.getLogger();

  public static final String TRACE_PATH = "traces";

  private final BlockReplay blockReplay;

  public TransactionTracer(final BlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<TransactionTrace> traceTransaction(
      final Hash blockHash, final Hash transactionHash, final DebugOperationTracer tracer) {
    return blockReplay.beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, header, blockchain, mutableWorldState, transactionProcessor) -> {
          final Stopwatch timer = Stopwatch.createStarted();
          final Result result =
              transactionProcessor.processTransaction(
                  blockchain,
                  mutableWorldState.updater(),
                  header,
                  transaction,
                  header.getCoinbase(),
                  tracer,
                  new BlockHashLookup(header, blockchain),
                  false);
          timer.stop();
          return new TransactionTrace(
              transaction, result, tracer.getTraceFrames(), timer.elapsed(TimeUnit.NANOSECONDS));
        });
  }

  public Optional<TransactionTrace> traceTransaction(
      final Hash blockHash, final Hash transactionHash, final StandardJsonTracer tracer) {
    return blockReplay.beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, header, blockchain, mutableWorldState, transactionProcessor) -> {
          final Stopwatch timer = Stopwatch.createStarted();
          final Result result =
              transactionProcessor.processTransaction(
                  blockchain,
                  mutableWorldState.updater(),
                  header,
                  transaction,
                  header.getCoinbase(),
                  tracer,
                  new BlockHashLookup(header, blockchain),
                  false,
                  new TransactionValidationParams.Builder().allowFutureNonce(true).build());

          timer.stop();
          return new TransactionTrace(
              transaction, result, new ArrayList<>(), timer.elapsed(TimeUnit.NANOSECONDS));
        });
  }

  public List<String> traceTransactionToFile(
      final Hash blockHash,
      final List<Transaction> transactions,
      final Optional<TransactionTraceParams> transactionTraceParams,
      final Path traceDir) {
    final List<String> traces = new ArrayList<>();
    try {
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
        throw new IOException(
            String.format("Trace directory '%s' does not exist and could not be made.", traceDir));
      }
      for (int i = 0; i < transactions.size(); i++) {
        final Transaction transaction = transactions.get(i);
        if (selectedHash.isEmpty()
            || selectedHash.filter(isEqual(transaction.getHash())).isPresent()) {
          final File traceFile =
              traceDir
                  .resolve(
                      String.format(
                          "block_%.10s-%d-%.10s-%s",
                          blockHash.toHexString(),
                          i,
                          transaction.getHash().toHexString(),
                          System.currentTimeMillis()))
                  .toFile();
          try (PrintStream out = new PrintStream(new FileOutputStream(traceFile))) {
            traceTransaction(
                    blockHash, transaction.getHash(), new StandardJsonTracer(out, showMemory))
                .ifPresent(
                    trace ->
                        out.println(
                            StandardJsonTracer.summaryTrace(
                                transaction, trace.getTime().orElseThrow(), trace.getResult())));
            traces.add(traceFile.getPath());
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Unable to create transaction trace : {}", e.getMessage());
      throw new RuntimeException(e.getMessage(), e);
    }
    return traces;
  }
}
