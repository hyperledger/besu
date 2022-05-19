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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerReputation implements Comparable<PeerReputation> {
  private static final Logger LOG = LoggerFactory.getLogger(PeerReputation.class);

  private final ConcurrentMap<Integer, AtomicInteger> timeoutCountByRequestType =
      new ConcurrentHashMap<>();

  private static final int DEFAULT_SCORE = 20;
  private static final int LARGE_ADJUSTMENT = 5;

  private int score = DEFAULT_SCORE;

  public Optional<DisconnectReason> recordRequestTimeout() {
    score -= LARGE_ADJUSTMENT;
    if (score <= 0) {
      LOG.debug("Disconnection triggered by timeout");
      return Optional.of(DisconnectReason.TIMEOUT);
    } else {
      return Optional.empty();
    }
  }

  public Map<Integer, AtomicInteger> timeoutCounts() {
    return timeoutCountByRequestType;
  }

  public Optional<DisconnectReason> recordUselessResponse() {
    score -= LARGE_ADJUSTMENT;
    if (score <= 0) {
      LOG.debug("Disconnection triggered by by useless response");
      return Optional.of(DisconnectReason.USELESS_PEER);
    } else {
      return Optional.empty();
    }
  }

  public void recordUselessPeer() {
    score -= LARGE_ADJUSTMENT;
  }

  @Override
  public String toString() {
    return String.format("PeerReputation " + score);
  }

  @Override
  public int compareTo(final @Nonnull PeerReputation otherReputation) {
    return Integer.compare(this.score, otherReputation.score);
  }
}
