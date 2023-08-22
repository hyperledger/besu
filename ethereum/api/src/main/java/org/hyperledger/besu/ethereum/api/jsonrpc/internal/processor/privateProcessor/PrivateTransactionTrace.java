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
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.List;
import java.util.Optional;

public class PrivateTransactionTrace {

  private final ExecutedPrivateTransaction privateTransaction;
  private final TransactionProcessingResult result;
  private final List<TraceFrame> traceFrames;
  private final Optional<Block> block;

  public PrivateTransactionTrace(final Optional<Block> block) {
    this.privateTransaction = null;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames) {
    this.privateTransaction = privateTransaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = Optional.empty();
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames,
      final Optional<Block> block) {
    this.privateTransaction = privateTransaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = block;
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction, final Optional<Block> block) {
    this.privateTransaction = privateTransaction;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  public ExecutedPrivateTransaction getPrivateTransaction() {
    return privateTransaction;
  }

  public long getGas() {
    return privateTransaction.getGasLimit() - result.getGasRemaining();
  }

  public long getGasLimit() {
    return privateTransaction.getGasLimit();
  }

  public TransactionProcessingResult getResult() {
    return result;
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }

  public Optional<Block> getBlock() {
    return block;
  }
}
