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

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

/** Contains the outputs of processing a block. */
public class BlockProcessingOutputs {

  private final MutableWorldState worldState;
  private final List<TransactionReceipt> receipts;
  private final Optional<List<Request>> maybeRequests;

  /**
   * Creates a new instance.
   *
   * @param worldState the world state after processing the block
   * @param receipts the receipts produced by processing the block
   */
  public BlockProcessingOutputs(
      final MutableWorldState worldState, final List<TransactionReceipt> receipts) {
    this(worldState, receipts, Optional.empty());
  }

  /**
   * Creates a new instance.
   *
   * @param worldState the world state after processing the block
   * @param receipts the receipts produced by processing the block
   * @param maybeRequests the requests produced by processing the block
   */
  public BlockProcessingOutputs(
      final MutableWorldState worldState,
      final List<TransactionReceipt> receipts,
      final Optional<List<Request>> maybeRequests) {
    this.worldState = worldState;
    this.receipts = receipts;
    this.maybeRequests = maybeRequests;
  }

  /**
   * Returns the world state after processing the block.
   *
   * @return the world state after processing the block
   */
  public MutableWorldState getWorldState() {
    return worldState;
  }

  /**
   * Returns the receipts produced by processing the block.
   *
   * @return the receipts produced by processing the block
   */
  public List<TransactionReceipt> getReceipts() {
    return receipts;
  }

  /**
   * Returns the requests produced by processing the block.
   *
   * @return the requests produced by processing the block
   */
  public Optional<List<Request>> getRequests() {
    return maybeRequests;
  }
}
