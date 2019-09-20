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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor.Result;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;

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
          return new TransactionTrace(transaction, result, tracer.getTraceFrames());
        });
  }
}
