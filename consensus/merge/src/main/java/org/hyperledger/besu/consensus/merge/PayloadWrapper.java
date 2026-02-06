/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreationTiming;
import org.hyperledger.besu.ethereum.core.BlockValueCalculator;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.List;
import java.util.Optional;

/** Wrapper for payload plus extra info. */
public class PayloadWrapper implements Comparable<PayloadWrapper> {
  private final PayloadIdentifier payloadIdentifier;
  private final BlockWithReceipts blockWithReceipts;
  private final Wei blockValue;
  private final int txCount;
  private final Optional<List<Request>> requests;
  private final BlockCreationTiming blockCreationTimings;
  private final Optional<BlockAccessList> blockAccessList;

  /**
   * Construct a wrapper with the following fields.
   *
   * @param payloadIdentifier Payload identifier
   * @param blockWithReceipts Block with receipts
   * @param blockCreationTimings The timings from block creation
   */
  public PayloadWrapper(
      final PayloadIdentifier payloadIdentifier,
      final BlockWithReceipts blockWithReceipts,
      final Optional<BlockAccessList> blockAccessList,
      final Optional<List<Request>> requests,
      final BlockCreationTiming blockCreationTimings) {
    this.blockWithReceipts = blockWithReceipts;
    this.payloadIdentifier = payloadIdentifier;
    this.blockValue = BlockValueCalculator.calculateBlockValue(blockWithReceipts);
    this.blockAccessList = blockAccessList;
    this.txCount = blockWithReceipts.getBlock().getBody().getTransactions().size();
    this.requests = requests;
    this.blockCreationTimings = blockCreationTimings;
  }

  /**
   * Get the block value
   *
   * @return block value in Wei
   */
  public Wei blockValue() {
    return blockValue;
  }

  /**
   * Get the number of transactions in the block
   *
   * @return the number of transactions
   */
  public int transactionCount() {
    return txCount;
  }

  /**
   * Get this payload identifier
   *
   * @return payload identifier
   */
  public PayloadIdentifier payloadIdentifier() {
    return payloadIdentifier;
  }

  /**
   * Get the block with receipts
   *
   * @return block with receipts
   */
  public BlockWithReceipts blockWithReceipts() {
    return blockWithReceipts;
  }

  /**
   * Get the requests
   *
   * @return requests
   */
  public Optional<List<Request>> requests() {
    return requests;
  }

  public Optional<BlockAccessList> blockAccessList() {
    return blockAccessList;
  }

  /**
   * Get the timings from block creation
   *
   * @return the timings from block creatiom
   */
  public BlockCreationTiming getBlockCreationTimings() {
    return blockCreationTimings;
  }

  /**
   * Compares this PayloadWrapper with another for ordering.
   *
   * <p>Comparison is performed in three stages:
   *
   * <ol>
   *   <li>Primary: Compare by block value (Wei). Higher value is considered greater.
   *   <li>Secondary: If block values are equal, compare by transaction count. Higher transaction
   *       count is considered greater.
   *   <li>Tertiary: If transaction counts are also equals, compare by starting time. Payload build
   *       before are considered greater.
   * </ol>
   *
   * @param o the PayloadWrapper to compare with
   * @return a negative integer, zero, or a positive integer as this PayloadWrapper is less than,
   *     equal to, or greater than the specified PayloadWrapper
   */
  @Override
  public int compareTo(final PayloadWrapper o) {
    final var cmpValue = blockValue().compareTo(o.blockValue());
    if (cmpValue == 0) {
      final var cmpTxCount = Integer.compare(transactionCount(), o.transactionCount());
      if (cmpTxCount == 0) {
        return -getBlockCreationTimings()
            .startedAt()
            .compareTo(o.getBlockCreationTimings().startedAt());
      }
      return cmpTxCount;
    }
    return cmpValue;
  }
}
