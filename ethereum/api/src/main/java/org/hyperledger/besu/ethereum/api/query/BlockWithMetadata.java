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
package org.hyperledger.besu.ethereum.api.query;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.List;

public class BlockWithMetadata<T, O> {

  private final BlockHeader header;
  private final List<T> transactions;
  private final List<O> ommers;
  private final Difficulty totalDifficulty;
  private final int size;

  /**
   * @param header The block header
   * @param transactions Block transactions in generic format
   * @param ommers Block ommers in generic format
   * @param totalDifficulty The cumulative difficulty up to and including this block
   * @param size The size of the rlp-encoded block (header + body).
   */
  public BlockWithMetadata(
      final BlockHeader header,
      final List<T> transactions,
      final List<O> ommers,
      final Difficulty totalDifficulty,
      final int size) {
    this.header = header;
    this.transactions = transactions;
    this.ommers = ommers;
    this.totalDifficulty = totalDifficulty;
    this.size = size;
  }

  public BlockHeader getHeader() {
    return header;
  }

  public List<O> getOmmers() {
    return ommers;
  }

  public List<T> getTransactions() {
    return transactions;
  }

  public Difficulty getTotalDifficulty() {
    return totalDifficulty;
  }

  public int getSize() {
    return size;
  }
}
