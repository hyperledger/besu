/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/**
 * A mutable {@link BftConfigOptions} that is used for building config for transitions in the {@link
 * ForksSchedule}.
 */
public class MutableBftConfigOptions implements BftConfigOptions {
  private long epochLength;
  private int blockPeriodSeconds;
  private int requestTimeoutSeconds;
  private int gossipedHistoryLimit;
  private int messageQueueLimit;
  private int duplicateMessageLimit;
  private int futureMessagesLimit;
  private int futureMessageMaxDistance;
  private Optional<Address> miningBeneficiary;
  private BigInteger blockRewardWei;

  public MutableBftConfigOptions(final BftConfigOptions bftConfigOptions) {
    this.epochLength = bftConfigOptions.getEpochLength();
    this.blockPeriodSeconds = bftConfigOptions.getBlockPeriodSeconds();
    this.requestTimeoutSeconds = bftConfigOptions.getRequestTimeoutSeconds();
    this.gossipedHistoryLimit = bftConfigOptions.getGossipedHistoryLimit();
    this.messageQueueLimit = bftConfigOptions.getMessageQueueLimit();
    this.duplicateMessageLimit = bftConfigOptions.getMessageQueueLimit();
    this.futureMessagesLimit = bftConfigOptions.getFutureMessagesLimit();
    this.futureMessageMaxDistance = bftConfigOptions.getFutureMessagesMaxDistance();
    this.miningBeneficiary = bftConfigOptions.getMiningBeneficiary();
    this.blockRewardWei = bftConfigOptions.getBlockRewardWei();
  }

  @Override
  public long getEpochLength() {
    return epochLength;
  }

  @Override
  public int getBlockPeriodSeconds() {
    return blockPeriodSeconds;
  }

  @Override
  public int getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  @Override
  public int getGossipedHistoryLimit() {
    return gossipedHistoryLimit;
  }

  @Override
  public int getMessageQueueLimit() {
    return messageQueueLimit;
  }

  @Override
  public int getDuplicateMessageLimit() {
    return duplicateMessageLimit;
  }

  @Override
  public int getFutureMessagesLimit() {
    return futureMessagesLimit;
  }

  @Override
  public int getFutureMessagesMaxDistance() {
    return futureMessageMaxDistance;
  }

  @Override
  public Optional<Address> getMiningBeneficiary() {
    return miningBeneficiary;
  }

  @Override
  public BigInteger getBlockRewardWei() {
    return blockRewardWei;
  }

  @Override
  public Map<String, Object> asMap() {
    return Map.of();
  }

  public void setEpochLength(final long epochLength) {
    this.epochLength = epochLength;
  }

  public void setBlockPeriodSeconds(final int blockPeriodSeconds) {
    this.blockPeriodSeconds = blockPeriodSeconds;
  }

  public void setRequestTimeoutSeconds(final int requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  public void setGossipedHistoryLimit(final int gossipedHistoryLimit) {
    this.gossipedHistoryLimit = gossipedHistoryLimit;
  }

  public void setMessageQueueLimit(final int messageQueueLimit) {
    this.messageQueueLimit = messageQueueLimit;
  }

  public void setDuplicateMessageLimit(final int duplicateMessageLimit) {
    this.duplicateMessageLimit = duplicateMessageLimit;
  }

  public void setFutureMessagesLimit(final int futureMessagesLimit) {
    this.futureMessagesLimit = futureMessagesLimit;
  }

  public void setFutureMessageMaxDistance(final int futureMessageMaxDistance) {
    this.futureMessageMaxDistance = futureMessageMaxDistance;
  }

  public void setMiningBeneficiary(final Optional<Address> miningBeneficiary) {
    checkNotNull(miningBeneficiary);
    this.miningBeneficiary = miningBeneficiary;
  }

  public void setBlockRewardWei(final BigInteger blockRewardWei) {
    this.blockRewardWei = blockRewardWei;
  }
}
