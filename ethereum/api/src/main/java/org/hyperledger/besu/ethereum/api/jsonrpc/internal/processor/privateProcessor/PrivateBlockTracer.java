/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateBlockTracer {
  private final PrivateBlockReplay blockReplay;
  // Either the initial block state or the state of the prior TX, including miner rewards.
  private WorldUpdater chainedUpdater;

  public PrivateBlockTracer(final PrivateBlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<PrivateBlockTrace> trace(
      final PrivateTracer.TraceableState mutableWorldState,
      final Block block,
      final DebugOperationTracer tracer,
      final String enclaveKey,
      final String privacyGroupId,
      final PrivateBlockMetadata privateBlockMetadata) {
    return blockReplay.block(
        block,
        privateBlockMetadata,
        enclaveKey,
        prepareReplayAction(mutableWorldState, tracer, privacyGroupId));
  }

  private PrivateBlockReplay.TransactionAction<PrivateTransactionTrace> prepareReplayAction(
      final PrivateTracer.TraceableState mutableWorldState,
      final DebugOperationTracer tracer,
      final String privacyGroupId) {
    return (transaction, header, blockchain, transactionProcessor) -> {
      // if we have no prior updater, it must be the first TX, so use the block's initial state
      if (chainedUpdater == null) {
        chainedUpdater = mutableWorldState.updater();

      } else if (chainedUpdater instanceof StackedUpdater<?, ?> stackedUpdater) {
        stackedUpdater.markTransactionBoundary();
      }
      // create an updater for just this tx
      chainedUpdater = chainedUpdater.updater();
      WorldUpdater privateChainedUpdater = mutableWorldState.privateUpdater();
      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              chainedUpdater,
              privateChainedUpdater,
              header,
              transaction.getPmtHash(),
              transaction,
              header.getCoinbase(),
              tracer,
              blockReplay
                  .getProtocolSpec(header)
                  .getBlockHashProcessor()
                  .createBlockHashLookup(blockchain, header),
              Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)));

      final List<TraceFrame> traceFrames = tracer.copyTraceFrames();
      tracer.reset();
      return new PrivateTransactionTrace(transaction, result, traceFrames);
    };
  }
}
