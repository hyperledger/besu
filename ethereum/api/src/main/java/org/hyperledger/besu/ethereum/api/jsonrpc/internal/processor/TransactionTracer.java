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
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor.Result;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.StandardJsonTracer;

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
        (transaction, header, blockchain, worldUpdater, transactionProcessor) -> {
          final Result result =
              processTransaction(
                  header, blockchain, worldUpdater, transaction, transactionProcessor, tracer);
          return new TransactionTrace(transaction, result, tracer.getTraceFrames());
        });
  }

  public List<String> traceTransactionToFile(
      final Hash blockHash,
      final List<Transaction> transactions,
      final Optional<TransactionTraceParams> transactionTraceParams,
      final Path traceDir) {
    final List<String> traces = new ArrayList<>();

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

    blockReplay.performActionWithBlock(
        blockHash,
        (body, header, blockchain, worldUpdater, transactionProcessor) -> {
          for (int i = 0; i < transactions.size(); i++) {
            final Transaction transaction = transactions.get(i);
            if (selectedHash.isEmpty()
                || selectedHash.filter(isEqual(transaction.getHash())).isPresent()) {
              final File traceFile = generateTraceFile(traceDir, blockHash, i, transaction);
              try (PrintStream out = new PrintStream(new FileOutputStream(traceFile))) {
                final Stopwatch timer = Stopwatch.createStarted();
                final Result result =
                    processTransaction(
                        header,
                        blockchain,
                        worldUpdater,
                        transaction,
                        transactionProcessor,
                        new StandardJsonTracer(out, showMemory));
                out.println(
                    StandardJsonTracer.summaryTrace(
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
                  worldUpdater,
                  transaction,
                  transactionProcessor,
                  OperationTracer.NO_TRACING);
            }
          }
          return Optional.empty();
        });

    return traces;
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

  private Result processTransaction(
      final BlockHeader header,
      final Blockchain blockchain,
      final WorldUpdater worldUpdater,
      final Transaction transaction,
      final TransactionProcessor transactionProcessor,
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
        new TransactionValidationParams.Builder().allowFutureNonce(true).build());
  }
}
