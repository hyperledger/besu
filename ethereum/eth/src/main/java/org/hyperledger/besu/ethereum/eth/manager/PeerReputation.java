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
  private static final Logger LOG = LoggerFactory.getLogger(PeerReputation.class);
  private static final int TIMEOUT_THRESHOLD = 3;
  private static final int USELESS_RESPONSE_THRESHOLD = 5;
  static final long USELESS_RESPONSE_WINDOW_IN_MILLIS =
      TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

  private final ConcurrentMap<Integer, AtomicInteger> timeoutCountByRequestType =
      new ConcurrentHashMap<>();
  private final Queue<Long> uselessResponseTimes = new ConcurrentLinkedQueue<>();

  private static final int DEFAULT_SCORE = 100;
  private static final int SMALL_ADJUSTMENT = 1;
  private static final int LARGE_ADJUSTMENT = 10;

  private long score = DEFAULT_SCORE;

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

  public void recordUsefulResposne() {
    score += SMALL_ADJUSTMENT;
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
    return Long.compare(this.score, otherReputation.score);
  }
}
