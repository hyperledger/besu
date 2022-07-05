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
package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.List;
import java.util.Optional;

public interface BlockValidator {

  class Result {
    public final Optional<BlockProcessingOutputs> blockProcessingOutputs;
    public final Optional<String> errorMessage;

    public Result(final BlockProcessingOutputs blockProcessingOutputs) {
      this.blockProcessingOutputs = Optional.of(blockProcessingOutputs);
      this.errorMessage = Optional.empty();
    }

    public Result(final String errorMessage) {
      this.blockProcessingOutputs = Optional.empty();
      this.errorMessage = Optional.of(errorMessage);
    }
  }

  class BlockProcessingOutputs {
    public final MutableWorldState worldState;
    public final List<TransactionReceipt> receipts;

    public BlockProcessingOutputs(
        final MutableWorldState worldState, final List<TransactionReceipt> receipts) {
      this.worldState = worldState;
      this.receipts = receipts;
    }
  }

  Result validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode);

  Result validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist);

  boolean fastBlockValidation(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode);
}
