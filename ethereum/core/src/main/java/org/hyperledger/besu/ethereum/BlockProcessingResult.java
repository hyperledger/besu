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
package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contains the outputs of processing a block. */
public class BlockProcessingResult extends BlockValidationResult {

  private final Optional<BlockProcessingOutputs> yield;
  private final boolean isPartial;
  private Optional<Integer> nbParallelizedTransations = Optional.empty();

  /** A result indicating that processing failed. */
  public static final BlockProcessingResult FAILED = new BlockProcessingResult("processing failed");

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   */
  public BlockProcessingResult(final Optional<BlockProcessingOutputs> yield) {
    this.yield = yield;
    this.isPartial = false;
  }

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   * @param nbParallelizedTransations potential number of parallelized transactions during block
   *     processing
   */
  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield,
      final Optional<Integer> nbParallelizedTransations) {
    this.yield = yield;
    this.isPartial = false;
    this.nbParallelizedTransations = nbParallelizedTransations;
  }

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   * @param isPartial whether the processing was incomplete
   */
  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final boolean isPartial) {
    this.yield = yield;
    this.isPartial = isPartial;
  }

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   * @param errorMessage the error message if any
   */
  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final String errorMessage) {
    super(errorMessage);
    this.yield = yield;
    this.isPartial = false;
  }

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   * @param cause the cause of the error if any
   */
  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final Throwable cause) {
    super(cause.getLocalizedMessage(), cause);
    this.yield = yield;
    this.isPartial = false;
  }

  /**
   * A result indicating that processing was successful but incomplete.
   *
   * @param yield the outputs of processing a block
   * @param errorMessage the error message if any
   * @param isPartial whether the processing was incomplete
   */
  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield,
      final String errorMessage,
      final boolean isPartial) {
    super(errorMessage);
    this.yield = yield;
    this.isPartial = isPartial;
  }

  /**
   * A result indicating that processing failed.
   *
   * @param errorMessage the error message
   */
  public BlockProcessingResult(final String errorMessage) {
    super(errorMessage);
    this.isPartial = false;
    this.yield = Optional.empty();
  }

  /**
   * Gets the block processing outputs of the result.
   *
   * @return the block processing outputs of the result
   */
  public Optional<BlockProcessingOutputs> getYield() {
    return yield;
  }

  /**
   * Checks if the processing was incomplete.
   *
   * @return true if the processing was incomplete, false otherwise
   */
  public boolean isPartial() {
    return isPartial;
  }

  /**
   * Gets the transaction receipts of the result.
   *
   * @return the transaction receipts of the result
   */
  public List<TransactionReceipt> getReceipts() {
    if (yield.isEmpty()) {
      return new ArrayList<>();
    } else {
      return yield.get().getReceipts();
    }
  }

  /**
   * Gets the requests of the result.
   *
   * @return the requests of the result
   */
  public Optional<List<Request>> getRequests() {
    return yield.flatMap(BlockProcessingOutputs::getRequests);
  }

  /**
   * Returns an optional that contains the number of parallelized transactions (if there is any)
   *
   * @return Optional of parallelized transactions during the block execution
   */
  public Optional<Integer> getNbParallelizedTransations() {
    return nbParallelizedTransations;
  }
}
