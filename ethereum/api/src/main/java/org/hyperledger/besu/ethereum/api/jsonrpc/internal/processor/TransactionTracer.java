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
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor.Result;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.EVMToolTracer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

/** Used to produce debug traces of transactions */
public class TransactionTracer {

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
      final Hash blockHash, final Hash transactionHash, final EVMToolTracer tracer) {
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
      final Optional<TransactionTraceParams> transactionTraceParams) {
    final List<String> traces = new ArrayList<>();
    try {
      final Optional<Hash> selectedHash =
          transactionTraceParams.flatMap(TransactionTraceParams::getTransactionHash);
      final boolean showMemory =
          transactionTraceParams.map(TransactionTraceParams::disableMemory).orElse(true);
      for (int i = 0; i < transactions.size(); i++) {
        final Transaction transaction = transactions.get(i);
        if (selectedHash.isEmpty()
            || selectedHash.filter(isEqual(transaction.getHash())).isPresent()) {
          final File tmpFile =
              File.createTempFile(
                  String.format(
                      "block_%.10s-%d-%.10s-",
                      blockHash.toHexString(), i, transaction.getHash().toHexString()),
                  "");
          try (PrintStream out = new PrintStream(new FileOutputStream(tmpFile))) {
            traceTransaction(blockHash, transaction.getHash(), new EVMToolTracer(out, showMemory))
                .ifPresent(
                    trace ->
                        out.println(
                            EVMToolTracer.summaryTrace(
                                transaction, trace.getTime().orElseThrow(), trace.getResult())));
            traces.add(tmpFile.getPath());
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(String.format("Unable to trace transaction : %s", e.getMessage()));
    }
    return traces;
  }
}
