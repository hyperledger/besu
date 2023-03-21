/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.Optional;
import java.util.function.Supplier;

public class BlockchainServiceImpl implements BlockchainService {

  private final Blockchain blockchain;

  public BlockchainServiceImpl(final Blockchain blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public Optional<BlockContext> getBlockByNumber(final long number) {
    return blockchain
        .getBlockByNumber(number)
        .map(block -> blockContext(block::getHeader, block::getBody));
  }

  private static BlockContext blockContext(
      final Supplier<BlockHeader> blockHeaderSupplier,
      final Supplier<BlockBody> blockBodySupplier) {
    return new BlockContext() {
      @Override
      public BlockHeader getBlockHeader() {
        return blockHeaderSupplier.get();
      }

      @Override
      public BlockBody getBlockBody() {
        return blockBodySupplier.get();
      }
    };
  }
}
