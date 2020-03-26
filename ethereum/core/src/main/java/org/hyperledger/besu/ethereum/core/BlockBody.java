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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BlockBody implements org.hyperledger.besu.plugin.data.BlockBody {

  private static final BlockBody EMPTY =
      new BlockBody(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

  private final List<Transaction> transactions;
  private final List<BlockHeader> ommers;
  private final List<TransactionReceipt> transactionReceipts;

  public BlockBody(
      final List<Transaction> transactions,
      final List<BlockHeader> ommers,
      final List<TransactionReceipt> transactionReceipts) {
    this.transactions = transactions;
    this.ommers = ommers;
    this.transactionReceipts = transactionReceipts;
  }

  public static BlockBody empty() {
    return EMPTY;
  }

  /** @return The list of transactions of the block. */
  @Override
  public List<Transaction> getTransactions() {
    return transactions;
  }

  /** @return The list of ommers of the block. */
  @Override
  public List<BlockHeader> getOmmers() {
    return ommers;
  }

  /** @return The list of transaction receipts of the block. */
  @Override
  public List<TransactionReceipt> getTransactionReceipts() {
    return transactionReceipts;
  }

  /**
   * Writes Block to {@link RLPOutput}.
   *
   * @param output Output to write to
   */
  public void writeTo(final RLPOutput output) {
    output.startList();
    output.writeList(getTransactions(), Transaction::writeTo);
    output.writeList(getOmmers(), BlockHeader::writeTo);
    output.writeList(getTransactionReceipts(), TransactionReceipt::writeTo);
    output.endList();
  }

  public static BlockBody readFrom(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    // TODO: Support multiple hard fork transaction formats.
    final BlockBody body =
        new BlockBody(
            input.readList(Transaction::readFrom),
            input.readList(rlp -> BlockHeader.readFrom(rlp, blockHeaderFunctions)),
            input.readList(TransactionReceipt::readFrom));
    input.leaveList();
    return body;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BlockBody)) {
      return false;
    }
    final BlockBody other = (BlockBody) obj;
    return transactions.equals(other.transactions)
        && ommers.equals(other.ommers)
        && transactionReceipts.equals(other.transactionReceipts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactions, ommers, transactionReceipts);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("BlockBody{");
    sb.append("transactions=").append(transactions).append(", ");
    sb.append("ommers=").append(ommers).append(", ");
    sb.append("transactionReceipts=").append(transactionReceipts);
    return sb.append("}").toString();
  }
}
