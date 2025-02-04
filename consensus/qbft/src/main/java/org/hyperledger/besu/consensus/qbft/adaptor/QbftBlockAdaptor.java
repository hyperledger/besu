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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;

/** Adaptor class to allow a {@link Block} to be used as a {@link QbftBlock}. */
public class QbftBlockAdaptor implements QbftBlock {

  private final BlockHeader header;
  private final Block block;

  /**
   * Constructs a QbftBlock from a Besu Block.
   *
   * @param block the Besu Block
   */
  public QbftBlockAdaptor(final Block block) {
    this.block = block;
    this.header = block.getHeader();
  }

  @Override
  public BlockHeader getHeader() {
    return header;
  }

  @Override
  public boolean isEmpty() {
    return block.getBody().getTransactions().isEmpty();
  }

  /**
   * Returns the Besu Block associated with this QbftBlock. Used to convert a QbftBlock back to a
   * Besu block.
   *
   * @return the Besu Block
   */
  public Block getBesuBlock() {
    return block;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof QbftBlockAdaptor qbftBlock)) return false;
    return Objects.equals(header, qbftBlock.header) && Objects.equals(block, qbftBlock.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(header, block);
  }
}
