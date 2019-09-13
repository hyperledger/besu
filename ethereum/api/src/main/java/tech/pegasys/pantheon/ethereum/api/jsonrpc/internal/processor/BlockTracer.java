/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.processor.BlockReplay.TransactionAction;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.DebugOperationTracer;

import java.util.Optional;

/** Used to produce debug traces of blocks */
public class BlockTracer {

  private final BlockReplay blockReplay;

  public BlockTracer(final BlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<BlockTrace> trace(final Hash blockHash, final DebugOperationTracer tracer) {
    return blockReplay.block(blockHash, prepareReplayAction(tracer));
  }

  public Optional<BlockTrace> trace(final Block block, final DebugOperationTracer tracer) {
    return blockReplay.block(block, prepareReplayAction(tracer));
  }

  private TransactionAction<TransactionTrace> prepareReplayAction(
      final DebugOperationTracer tracer) {
    return (transaction, header, blockchain, mutableWorldState, transactionProcessor) -> {
      final TransactionProcessor.Result result =
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
    };
  }
}
