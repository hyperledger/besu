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
package org.hyperledger.besu.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerReputation implements Comparable<PeerReputation> {
  static final long USELESS_RESPONSE_WINDOW_IN_MILLIS =
      TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
  static final int DEFAULT_MAX_SCORE = 150;
  static final int DEFAULT_INITIAL_SCORE = 100;
  private static final Logger LOG = LoggerFactory.getLogger(PeerReputation.class);
  private static final int TIMEOUT_THRESHOLD = 3;
  private static final int USELESS_RESPONSE_THRESHOLD = 5;

  private final ConcurrentMap<Integer, AtomicInteger> timeoutCountByRequestType =
      new ConcurrentHashMap<>();
  private final Queue<Long> uselessResponseTimes = new ConcurrentLinkedQueue<>();

  private static final int SMALL_ADJUSTMENT = 1;
  private static final int LARGE_ADJUSTMENT = 10;

  private int score;

  private final int maxScore;

  public PeerReputation() {
    this(DEFAULT_INITIAL_SCORE, DEFAULT_MAX_SCORE);
  }

  public PeerReputation(final int initialScore, final int maxScore) {
    checkArgument(initialScore <= maxScore, "Inital score must be less than or equal to max score");
    this.maxScore = maxScore;
    this.score = initialScore;
  }

  public Optional<DisconnectReason> recordRequestTimeout(final int requestCode) {
    final int newTimeoutCount = getOrCreateTimeoutCount(requestCode).incrementAndGet();
    if (newTimeoutCount >= TIMEOUT_THRESHOLD) {
      LOG.debug("Disconnection triggered by repeated timeouts");
      score -= LARGE_ADJUSTMENT;
      return Optional.of(DisconnectReason.TIMEOUT);
    } else {
      score -= SMALL_ADJUSTMENT;
      return Optional.empty();
    }
  }

  public void resetTimeoutCount(final int requestCode) {
    timeoutCountByRequestType.remove(requestCode);
  }

  private AtomicInteger getOrCreateTimeoutCount(final int requestCode) {
    return timeoutCountByRequestType.computeIfAbsent(requestCode, code -> new AtomicInteger());
  }

  public Map<Integer, AtomicInteger> timeoutCounts() {
    return timeoutCountByRequestType;
  }

  public Optional<DisconnectReason> recordUselessResponse(final long timestamp) {
    uselessResponseTimes.add(timestamp);
    while (shouldRemove(uselessResponseTimes.peek(), timestamp)) {
      uselessResponseTimes.poll();
    }
    if (uselessResponseTimes.size() >= USELESS_RESPONSE_THRESHOLD) {
      score -= LARGE_ADJUSTMENT;
      LOG.debug("Disconnection triggered by exceeding useless response threshold");
      return Optional.of(DisconnectReason.USELESS_PEER);
    } else {
      score -= SMALL_ADJUSTMENT;
      return Optional.empty();
    }
  }

  public void recordUsefulResponse() {
    if (score < maxScore) {
      score = Math.min(maxScore, score + SMALL_ADJUSTMENT);
    }
  }

  private boolean shouldRemove(final Long timestamp, final long currentTimestamp) {
    return timestamp != null && timestamp + USELESS_RESPONSE_WINDOW_IN_MILLIS < currentTimestamp;
  }

  @Override
  public String toString() {
    return String.format("PeerReputation " + score);
  }

  @Override
  public int compareTo(final @Nonnull PeerReputation otherReputation) {
    return Integer.compare(this.score, otherReputation.score);
  }

  public int getScore() {
    return score;
  }
}
