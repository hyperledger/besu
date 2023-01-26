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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

/** Head of a blockchain. */
public final class ChainHead {

  private final Hash hash;

  private final BlockHeader blockHeader;

  private final Difficulty totalDifficulty;

  private final long height;

  public ChainHead(final BlockHeader header, final Difficulty totalDifficulty, final long height) {
    this.blockHeader = header;
    this.hash = header.getHash();
    this.totalDifficulty = totalDifficulty;
    this.height = height;
  }

  public Hash getHash() {
    return hash;
  }

  public Difficulty getTotalDifficulty() {
    return totalDifficulty;
  }

  public long getHeight() {
    return height;
  }

  public String toLogString() {
    return getHeight() + " (" + getHash().toHexString() + ")";
  }

  public BlockHeader getBlockHeader() {
    return blockHeader;
  }
}
