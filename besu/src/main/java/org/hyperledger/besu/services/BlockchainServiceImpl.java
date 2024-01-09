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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The Blockchain service implementation. */
@Unstable
public class BlockchainServiceImpl implements BlockchainService {

  private final MutableBlockchain blockchain;

  /**
   * Instantiates a new Blockchain service.
   *
   * @param blockchain the blockchain
   */
  public BlockchainServiceImpl(final MutableBlockchain blockchain) {
    this.blockchain = blockchain;
  }

  /**
   * Gets block by number
   *
   * @param number the block number
   * @return the BlockContext if block exists otherwise empty
   */
  @Override
  public Optional<BlockContext> getBlockByNumber(final long number) {
    return blockchain
        .getBlockByNumber(number)
        .map(block -> blockContext(block::getHeader, block::getBody));
  }

  @Override
  public Optional<List<TransactionReceipt>> getReceiptsByBlockHash(final Hash blockHash) {
    return blockchain
        .getTxReceipts(blockHash)
        .map(
            list -> list.stream().map(TransactionReceipt.class::cast).collect(Collectors.toList()));
  }

  @Override
  public void storeBlock(
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final List<TransactionReceipt> receipts) {
    final org.hyperledger.besu.ethereum.core.BlockHeader coreHeader =
        (org.hyperledger.besu.ethereum.core.BlockHeader) blockHeader;
    final org.hyperledger.besu.ethereum.core.BlockBody coreBody =
        (org.hyperledger.besu.ethereum.core.BlockBody) blockBody;
    final List<org.hyperledger.besu.ethereum.core.TransactionReceipt> coreReceipts =
        receipts.stream()
            .map(org.hyperledger.besu.ethereum.core.TransactionReceipt.class::cast)
            .toList();
    blockchain.unsafeImportBlock(new Block(coreHeader, coreBody), coreReceipts,
            Optional.ofNullable(blockchain.calculateTotalDifficulty(coreHeader)));
  }

  @Override
  public BlockHeader getChainHead() {
    return blockchain.getChainHead().getBlockHeader();
  }

  @Override
  public Optional<Hash> getSafeBlock() {
    return blockchain.getSafeBlock();
  }

  @Override
  public Optional<Hash> getFinalizedBlock() {
    return blockchain.getFinalized();
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
