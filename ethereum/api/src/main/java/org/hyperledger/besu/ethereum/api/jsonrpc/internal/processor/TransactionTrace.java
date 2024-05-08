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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.List;
import java.util.Optional;

/** The type Transaction trace. */
public class TransactionTrace {

  private final Transaction transaction;
  private final TransactionProcessingResult result;
  private final List<TraceFrame> traceFrames;
  private final Optional<Block> block;

  /**
   * Instantiates a new Transaction trace.
   *
   * @param block the block
   */
  public TransactionTrace(final Optional<Block> block) {
    this.transaction = null;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  /**
   * Instantiates a new Transaction trace.
   *
   * @param transaction the transaction
   * @param result the result
   * @param traceFrames the trace frames
   */
  public TransactionTrace(
      final Transaction transaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames) {
    this.transaction = transaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = Optional.empty();
  }

  /**
   * Instantiates a new Transaction trace.
   *
   * @param transaction the transaction
   * @param result the result
   * @param traceFrames the trace frames
   * @param block the block
   */
  public TransactionTrace(
      final Transaction transaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames,
      final Optional<Block> block) {
    this.transaction = transaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = block;
  }

  /**
   * Instantiates a new Transaction trace.
   *
   * @param transaction the transaction
   * @param block the block
   */
  public TransactionTrace(final Transaction transaction, final Optional<Block> block) {
    this.transaction = transaction;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  /**
   * Gets transaction.
   *
   * @return the transaction
   */
  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Gets gas.
   *
   * @return the gas
   */
  public long getGas() {
    return transaction.getGasLimit() - result.getGasRemaining();
  }

  /**
   * Gets gas limit.
   *
   * @return the gas limit
   */
  public long getGasLimit() {
    return transaction.getGasLimit();
  }

  /**
   * Gets result.
   *
   * @return the result
   */
  public TransactionProcessingResult getResult() {
    return result;
  }

  /**
   * Gets trace frames.
   *
   * @return the trace frames
   */
  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }

  /**
   * Gets block.
   *
   * @return the block
   */
  public Optional<Block> getBlock() {
    return block;
  }
}
