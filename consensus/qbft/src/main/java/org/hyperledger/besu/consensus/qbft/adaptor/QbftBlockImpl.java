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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class QbftBlockImpl implements QbftBlock {

  private final BlockHeader header;
  private final Block block;

  public QbftBlockImpl(final Block block) {
    this.block = block;
    this.header = block.getHeader();
  }

  @Override
  public BlockHeader getHeader() {
    return header;
  }

  @Override
  public boolean isEmpty() {
    return header.getTransactionsRoot().equals(Hash.EMPTY_TRIE_HASH);
  }

  public Block getBesuBlock() {
    return block;
  }
}
