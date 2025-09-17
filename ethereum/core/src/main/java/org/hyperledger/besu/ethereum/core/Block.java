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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.encoding.BlockAccessListDecoder;
import org.hyperledger.besu.ethereum.core.encoding.BlockAccessListEncoder;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class Block {

  private final BlockHeader header;
  private final BlockBody body;
  private int size;

  public Block(final BlockHeader header, final BlockBody body, final int size) {
    this.header = header;
    this.body = body;
    this.size = size;
  }

  public Block(final BlockHeader header, final BlockBody body) {
    this.header = header;
    this.body = body;
    this.size = -1; // Size will be calculated on demand
  }

  public BlockHeader getHeader() {
    return header;
  }

  public BlockBody getBody() {
    return body;
  }

  public Hash getHash() {
    return header.getHash();
  }

  public Bytes toRlp() {
    return RLP.encode(this::writeTo);
  }

  public int getSize() {
    if (size < 0) {
      size = toRlp().size();
    }
    return size;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    header.writeTo(out);
    out.writeList(body.getTransactions(), Transaction::writeTo);
    out.writeList(body.getOmmers(), BlockHeader::writeTo);
    body.getWithdrawals().ifPresent(withdrawals -> out.writeList(withdrawals, Withdrawal::writeTo));
    body.getWithdrawals().ifPresent(withdrawals -> out.writeList(withdrawals, Withdrawal::writeTo));
    body.getBlockAccessList()
        .ifPresent(accessList -> BlockAccessListEncoder.encode(accessList, out));

    out.endList();
  }

  public static Block readFrom(final RLPInput in, final BlockHeaderFunctions hashFunction) {
    int size = in.currentSize();
    in.enterList();
    final BlockHeader header = BlockHeader.readFrom(in, hashFunction);
    final List<Transaction> transactions = in.readList(Transaction::readFrom);
    final List<BlockHeader> ommers = in.readList(rlp -> BlockHeader.readFrom(rlp, hashFunction));
    final Optional<List<Withdrawal>> withdrawals =
        in.isEndOfCurrentList() ? Optional.empty() : Optional.of(in.readList(Withdrawal::readFrom));
    final Optional<BlockAccessList> blockAccessList =
        in.isEndOfCurrentList() ? Optional.empty() : Optional.of(BlockAccessListDecoder.decode(in));
    in.leaveList();

    return new Block(
        header, new BlockBody(transactions, ommers, withdrawals, blockAccessList), size);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Block)) {
      return false;
    }
    final Block other = (Block) obj;
    return header.equals(other.header) && body.equals(other.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(header, body);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Block{");
    sb.append("header=").append(header).append(", ");
    sb.append("body=").append(body);
    return sb.append("}").toString();
  }

  public String toLogString() {
    return getHeader().toLogString();
  }
}
