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
package org.hyperledger.besu.services;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockSimulationService;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class BlockSimulatorServiceImpl implements BlockSimulationService {
  private final MiningCoordinator miningCoordinator;

  public BlockSimulatorServiceImpl(final MiningCoordinator miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public BlockContext simulate(
      final BlockHeader parentHeader,
      final List<? extends Transaction> transactions,
      final long timestamp) {

    org.hyperledger.besu.ethereum.core.BlockHeader parentHeaderCore =
        (org.hyperledger.besu.ethereum.core.BlockHeader) parentHeader;

    List<org.hyperledger.besu.ethereum.core.Transaction> coreTransactions =
        transactions.stream().map(t -> (org.hyperledger.besu.ethereum.core.Transaction) t).toList();

    Block block =
        miningCoordinator
            .createBlock(parentHeaderCore, coreTransactions, Collections.emptyList(), timestamp)
            .orElseThrow(() -> new IllegalArgumentException("Unable to create block."));

    return blockContext(block::getHeader, block::getBody);
  }

  private BlockContext blockContext(
      final Supplier<BlockHeader> headerSupplier, final Supplier<BlockBody> bodySupplier) {
    return new BlockContext() {
      @Override
      public BlockHeader getBlockHeader() {
        return headerSupplier.get();
      }

      @Override
      public BlockBody getBlockBody() {
        return bodySupplier.get();
      }
    };
  }
}
