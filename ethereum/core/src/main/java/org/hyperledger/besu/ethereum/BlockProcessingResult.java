/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockProcessingResult extends BlockValidationResult {

  private final Optional<BlockProcessingOutputs> yield;
  private final boolean isPartial;

  public static final BlockProcessingResult FAILED = new BlockProcessingResult("processing failed");

  public BlockProcessingResult(final Optional<BlockProcessingOutputs> yield) {
    this.yield = yield;
    this.isPartial = false;
  }

  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final boolean isPartial) {
    this.yield = yield;
    this.isPartial = isPartial;
  }

  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final String errorMessage) {
    super(errorMessage);
    this.yield = yield;
    this.isPartial = false;
  }

  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield, final Throwable cause) {
    super(cause.getLocalizedMessage(), cause);
    this.yield = yield;
    this.isPartial = false;
  }

  public BlockProcessingResult(
      final Optional<BlockProcessingOutputs> yield,
      final String errorMessage,
      final boolean isPartial) {
    super(errorMessage);
    this.yield = yield;
    this.isPartial = isPartial;
  }

  public BlockProcessingResult(final String errorMessage) {
    super(errorMessage);
    this.isPartial = false;
    this.yield = Optional.empty();
  }

  public Optional<BlockProcessingOutputs> getYield() {
    return yield;
  }

  public boolean isPartial() {
    return isPartial;
  }

  public List<TransactionReceipt> getReceipts() {
    if (yield.isEmpty()) {
      return new ArrayList<>();
    } else {
      return yield.get().getReceipts();
    }
  }
}
