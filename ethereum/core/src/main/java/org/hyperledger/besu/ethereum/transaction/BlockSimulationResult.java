/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;

import java.util.List;
import java.util.Optional;

public class BlockSimulationResult
    implements org.hyperledger.besu.plugin.data.BlockSimulationResult {
  final BlockHeader blockHeader;
  final BlockBody blockBody;
  final List<? extends TransactionReceipt> receipts;
  final BlockProcessingResult result;

  public BlockSimulationResult(final BlockProcessingResult result) {
    this.receipts = null;
    this.blockHeader = null;
    this.blockBody = null;
    this.result = result;
  }

  public BlockSimulationResult(
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final List<? extends TransactionReceipt> receipts,
      final BlockProcessingResult result) {
    this.blockHeader = blockHeader;
    this.blockBody = blockBody;
    this.receipts = receipts;
    this.result = result;
  }

  public BlockProcessingResult getResult() {
    return result;
  }

  @Override
  public Optional<BlockHeader> getBlockHeader() {
    return Optional.ofNullable(blockHeader);
  }

  @Override
  public Optional<BlockBody> getBlockBody() {
    return Optional.ofNullable(blockBody);
  }

  @Override
  public Optional<List<? extends TransactionReceipt>> getReceipts() {
    return Optional.ofNullable(receipts);
  }
}
