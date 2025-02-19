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
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Objects;

/** Adaptor class to allow a {@link Block} to be used as a {@link QbftBlock}. */
public class QbftBlockAdaptor implements QbftBlock {

  private final Block besuBlock;
  private final QbftBlockHeader qbftBlockHeader;

  /**
   * Constructs a QbftBlock from a Besu Block.
   *
   * @param besuBlock the Besu Block
   */
  public QbftBlockAdaptor(final Block besuBlock) {
    this.besuBlock = besuBlock;
    this.qbftBlockHeader = new QbftBlockHeaderAdaptor(besuBlock.getHeader());
  }

  @Override
  public QbftBlockHeader getHeader() {
    return qbftBlockHeader;
  }

  @Override
  public boolean isEmpty() {
    return besuBlock.getHeader().getTransactionsRoot().equals(Hash.EMPTY_TRIE_HASH);
  }

  /**
   * Returns the Besu Block associated with this QbftBlock. Used to convert a QbftBlock back to a
   * Besu block.
   *
   * @return the Besu Block
   */
  public Block getBesuBlock() {
    return besuBlock;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof QbftBlockAdaptor qbftBlock)) return false;
    return Objects.equals(besuBlock, qbftBlock.besuBlock)
        && Objects.equals(qbftBlockHeader, qbftBlock.qbftBlockHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(besuBlock, qbftBlockHeader);
  }
}
