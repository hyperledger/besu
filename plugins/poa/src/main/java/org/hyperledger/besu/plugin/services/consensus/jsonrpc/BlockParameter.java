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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Placeholder */
public class BlockParameter {

  private final BlockParameterType type;
  private final Optional<Long> number;

  /** Placeholder */
  public static final BlockParameter EARLIEST = new BlockParameter("earliest");

  /** Placeholder */
  public static final BlockParameter LATEST = new BlockParameter("latest");

  /** Placeholder */
  public static final BlockParameter PENDING = new BlockParameter("pending");

  /** Placeholder */
  public static final BlockParameter FINALIZED = new BlockParameter("finalized");

  /** Placeholder */
  public static final BlockParameter SAFE = new BlockParameter("safe");

  /**
   * Class that represents a block parameter
   *
   * @param value the string representation of the parameter
   */
  @JsonCreator
  public BlockParameter(final String value) {
    final String normalizedValue = value.toLowerCase(Locale.ROOT);

    switch (normalizedValue) {
      case "earliest":
        type = BlockParameterType.EARLIEST;
        number = Optional.of(BlockHeader.GENESIS_BLOCK_NUMBER);
        break;
      case "latest":
        type = BlockParameterType.LATEST;
        number = Optional.empty();
        break;
      case "pending":
        type = BlockParameterType.PENDING;
        number = Optional.empty();
        break;
      case "finalized":
        type = BlockParameterType.FINALIZED;
        number = Optional.empty();
        break;
      case "safe":
        type = BlockParameterType.SAFE;
        number = Optional.empty();
        break;
      default:
        type = BlockParameterType.NUMERIC;
        number = Optional.of(Long.decode(value));
        break;
    }
  }

  /**
   * Create new block parameter
   *
   * @param value the value as a long
   */
  public BlockParameter(final long value) {
    type = BlockParameterType.NUMERIC;
    number = Optional.of(value);
  }

  /**
   * Get the number the parameter represents
   *
   * @return the number
   */
  public Optional<Long> getNumber() {
    return number;
  }

  /**
   * Is the parameter for the pending block
   *
   * @return if it's a pending block parameter
   */
  public boolean isPending() {
    return this.type == BlockParameterType.PENDING;
  }

  /**
   * Is the parameter for the latest block
   *
   * @return if it's a latest block parameter
   */
  public boolean isLatest() {
    return this.type == BlockParameterType.LATEST;
  }

  /**
   * Is it the earliest?
   *
   * @return whether it's a request for the earliest block
   */
  public boolean isEarliest() {
    return this.type == BlockParameterType.EARLIEST;
  }

  /**
   * Is it the earliest?
   *
   * @return whether it's a request for the earliest block
   */
  public boolean isFinalized() {
    return this.type == BlockParameterType.FINALIZED;
  }

  /**
   * Is it the earliest?
   *
   * @return whether it's a request for the earliest block
   */
  public boolean isSafe() {
    return this.type == BlockParameterType.SAFE;
  }

  /**
   * Is it the earliest?
   *
   * @return whether it's a request for the earliest block
   */
  public boolean isNumeric() {
    return this.type == BlockParameterType.NUMERIC;
  }

  /**
   * Get the block number
   *
   * @param blockchain the blockchain service
   * @return the block number
   */
  public Optional<Long> getBlockNumber(final BlockchainService blockchain) {

    /*if (this.isFinalized()) {
        return blockchain.finalizedBlockHeader().map(ProcessableBlockHeader::getNumber);
    } else */
    if (this.isLatest()) {
      return Optional.of(blockchain.getChainHeadHeader().getNumber());
    }
    /*else if (this.isPending()) {
        // Pending not implemented, returns latest
        return Optional.of(blockchain.headBlockNumber());
    } else if (this.isSafe()) {
        return blockchain.safeBlockHeader().map(ProcessableBlockHeader::getNumber);
    } else {*/
    // Alternate cases (numeric input or "earliest")
    return this.getNumber();
    // }
  }

  @Override
  public String toString() {
    return "BlockParameter{" + "type=" + type + ", number=" + number + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlockParameter that = (BlockParameter) o;
    return type == that.type && number.equals(that.number);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, number);
  }

  private enum BlockParameterType {
    EARLIEST,
    LATEST,
    PENDING,
    NUMERIC,
    FINALIZED,
    SAFE
  }
}
