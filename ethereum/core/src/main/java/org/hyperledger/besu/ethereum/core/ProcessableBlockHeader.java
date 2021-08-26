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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** A block header capable of being processed. */
public class ProcessableBlockHeader implements org.hyperledger.besu.plugin.data.BlockHeader {

  protected final Hash parentHash;

  protected final Address coinbase;

  protected final Difficulty difficulty;

  protected final long number;

  protected final long gasLimit;

  // The block creation timestamp (seconds since the unix epoch)
  protected final long timestamp;
  // base fee is included for post EIP-1559 blocks
  protected final Long baseFee;

  protected ProcessableBlockHeader(
      final Hash parentHash,
      final Address coinbase,
      final Difficulty difficulty,
      final long number,
      final long gasLimit,
      final long timestamp,
      final Long baseFee) {
    this.parentHash = parentHash;
    this.coinbase = coinbase;
    this.difficulty = difficulty;
    this.number = number;
    this.gasLimit = gasLimit;
    this.timestamp = timestamp;
    this.baseFee = baseFee;
  }

  /**
   * Returns the block parent block hash.
   *
   * @return the block parent block hash
   */
  @Override
  public Hash getParentHash() {
    return parentHash;
  }

  @Override
  public Hash getOmmersHash() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  /**
   * Returns the block coinbase address.
   *
   * @return the block coinbase address
   */
  @Override
  public Address getCoinbase() {
    return coinbase;
  }

  @Override
  public Hash getStateRoot() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  @Override
  public Hash getTransactionsRoot() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  @Override
  public Hash getReceiptsRoot() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  @Override
  public Bytes getLogsBloom() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  /**
   * Returns the block difficulty.
   *
   * @return the block difficulty
   */
  @Override
  public Difficulty getDifficulty() {
    return difficulty;
  }

  /**
   * Returns the block number.
   *
   * @return the block number
   */
  @Override
  public long getNumber() {
    return number;
  }

  /**
   * Return the block gas limit.
   *
   * @return the block gas limit
   */
  @Override
  public long getGasLimit() {
    return gasLimit;
  }

  @Override
  public long getGasUsed() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  /**
   * Return the block timestamp.
   *
   * @return the block timestamp
   */
  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Bytes getExtraData() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  @Override
  public Hash getMixHash() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  @Override
  public long getNonce() {
    return 0;
  }

  @Override
  public Hash getBlockHash() {
    throw new UnsupportedOperationException("Not available in ProcessableBlockHeader");
  }

  /**
   * Returns the basefee of the block.
   *
   * @return the raw bytes of the extra data field
   */
  @Override
  public Optional<Long> getBaseFee() {
    return Optional.ofNullable(baseFee);
  }
}
