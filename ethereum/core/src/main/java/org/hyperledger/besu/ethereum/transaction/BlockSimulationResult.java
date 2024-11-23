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
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Optional;

public class BlockSimulationResult {
  final Block block;
  final BlockProcessingResult result;

  public BlockSimulationResult(final Block block, final BlockProcessingResult result) {
    this.block = block;
    this.result = result;
  }

  public BlockSimulationResult(final BlockProcessingResult result) {
    this.block = null;
    this.result = result;
  }

  public Optional<Block> getBlock() {
    return Optional.ofNullable(block);
  }

  public BlockProcessingResult getResult() {
    return result;
  }
}
