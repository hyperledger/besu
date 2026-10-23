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
package org.hyperledger.besu.plugins.graphql.internal.query;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.units.bigints.UInt256;

/** A block together with its total difficulty. */
public class BlockWithMetadata {

  private final BlockHeader header;
  private final BlockBody body;
  private final UInt256 totalDifficulty;

  /**
   * Creates a block with metadata.
   *
   * @param header the block header
   * @param body the block body
   * @param totalDifficulty the total difficulty of the chain up to and including the block
   */
  public BlockWithMetadata(
      final BlockHeader header, final BlockBody body, final UInt256 totalDifficulty) {
    this.header = header;
    this.body = body;
    this.totalDifficulty = totalDifficulty;
  }

  /**
   * Returns the block header.
   *
   * @return the block header
   */
  public BlockHeader getHeader() {
    return header;
  }

  /**
   * Returns the block body.
   *
   * @return the block body
   */
  public BlockBody getBody() {
    return body;
  }

  /**
   * Returns the total difficulty of the chain up to and including the block.
   *
   * @return the total difficulty
   */
  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }

  /**
   * Returns the transactions of the block with their location in it.
   *
   * @return the transactions
   */
  public List<TransactionWithMetadata> getTransactions() {
    final List<? extends Transaction> txs = body.getTransactions();
    final List<TransactionWithMetadata> result = new ArrayList<>(txs.size());
    for (int i = 0; i < txs.size(); i++) {
      result.add(
          new TransactionWithMetadata(
              txs.get(i),
              header.getNumber(),
              Quantities.toWei(header.getBaseFee()),
              header.getBlockHash(),
              i,
              header.getTimestamp()));
    }
    return result;
  }

  /**
   * Returns the hashes of the ommers of the block.
   *
   * @return the ommer hashes
   */
  public List<Hash> getOmmers() {
    return body.getOmmers().stream().map(BlockHeader::getBlockHash).toList();
  }
}
