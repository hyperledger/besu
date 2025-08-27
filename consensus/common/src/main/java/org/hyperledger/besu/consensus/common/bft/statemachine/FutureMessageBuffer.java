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
package org.hyperledger.besu.consensus.common.bft.statemachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.google.common.annotations.VisibleForTesting;

/**
 * Buffer which holds future BFT messages.
 *
 * <p>This buffer only allows messages to be added which have a chain height greater than current
 * height and up to chain futureMessagesMaxDistance from the current chain height.
 *
 * <p>When the total number of messages is greater futureMessagesLimit then messages are evicted.
 *
 * <p>If there is more than one height in the buffer then all messages for the highest chain height
 * are removed. Otherwise if there is only one height the oldest inserted message is removed.
 *
 * @param <T> the type of messages stored in this buffer
 */
public class FutureMessageBuffer<T> {
  private final NavigableMap<Long, List<T>> buffer = new TreeMap<>();
  private final long futureMessagesMaxDistance;
  private final long futureMessagesLimit;
  private final FutureMessageHandler<T> futureMessageHandler;
  private long chainHeight;

  /**
   * Future message handler, which is called when a future message is added to the buffer.
   *
   * @param <T> the type of message being handled
   */
  public interface FutureMessageHandler<T> {
    /**
     * Notify the handler of the future message being added to the buffer.
     *
     * @param msgChainHeight the msg chain height
     * @param message the message
     */
    void handleFutureMessage(long msgChainHeight, T message);
  }

  /**
   * Instantiates a new Future message buffer.
   *
   * @param futureMessagesMaxDistance the future messages max distance
   * @param futureMessagesLimit the future messages limit
   * @param chainHeight the chain height
   */
  public FutureMessageBuffer(
      final long futureMessagesMaxDistance,
      final long futureMessagesLimit,
      final long chainHeight) {
    this.futureMessagesMaxDistance = futureMessagesMaxDistance;
    this.futureMessagesLimit = futureMessagesLimit;
    this.chainHeight = chainHeight;
    this.futureMessageHandler = (msgChainHeight, message) -> {};
  }

  /**
   * Instantiates a new Future message buffer.
   *
   * @param futureMessagesMaxDistance the future messages max distance
   * @param futureMessagesLimit the future messages limit
   * @param chainHeight the chain height
   * @param futureMessageHandler the future message handler
   */
  public FutureMessageBuffer(
      final long futureMessagesMaxDistance,
      final long futureMessagesLimit,
      final long chainHeight,
      final FutureMessageHandler<T> futureMessageHandler) {
    this.futureMessagesMaxDistance = futureMessagesMaxDistance;
    this.futureMessagesLimit = futureMessagesLimit;
    this.chainHeight = chainHeight;
    this.futureMessageHandler = futureMessageHandler;
  }

  /**
   * Add message.
   *
   * @param msgChainHeight the msg chain height
   * @param rawMsg the message to add
   */
  public void addMessage(final long msgChainHeight, final T rawMsg) {
    futureMessageHandler.handleFutureMessage(msgChainHeight, rawMsg);

    if (futureMessagesLimit == 0 || !validMessageHeight(msgChainHeight, chainHeight)) {
      return;
    }

    addMessageToBuffer(msgChainHeight, rawMsg);

    if (totalMessagesSize() > futureMessagesLimit) {
      evictMessages();
    }
  }

  private void addMessageToBuffer(final long msgChainHeight, final T rawMsg) {
    buffer.putIfAbsent(msgChainHeight, new ArrayList<>());
    buffer.get(msgChainHeight).add(rawMsg);
  }

  private boolean validMessageHeight(final long msgChainHeight, final long currentHeight) {
    final boolean isFutureMsg = msgChainHeight > currentHeight;
    final boolean withinMaxChainHeight =
        msgChainHeight <= currentHeight + futureMessagesMaxDistance;
    return isFutureMsg && withinMaxChainHeight;
  }

  private void evictMessages() {
    if (buffer.size() > 1) {
      buffer.remove(buffer.lastKey());
    } else if (buffer.size() == 1) {
      List<T> messages = buffer.firstEntry().getValue();
      messages.remove(0);
    }
  }

  /**
   * Retrieve messages for height.
   *
   * @param height the height
   * @return the list
   */
  public List<T> retrieveMessagesForHeight(final long height) {
    chainHeight = height;
    final List<T> messages = buffer.getOrDefault(height, Collections.emptyList());
    discardPreviousHeightMessages();
    return messages;
  }

  /**
   * Retrieve and replay messages for height with replay handler.
   *
   * @param height the height
   * @param replayHandler the replay handler that will be called for each message with isReplay=true
   */
  public void retrieveAndReplayMessagesForHeight(
      final long height, final ReplayHandler<T> replayHandler) {
    chainHeight = height;
    final List<T> messages = buffer.getOrDefault(height, Collections.emptyList());
    messages.forEach(message -> replayHandler.handleReplayMessage(message, true));
    discardPreviousHeightMessages();
  }

  /**
   * Interface for handling replayed messages with replay flag.
   *
   * @param <T> the type of message being handled
   */
  public interface ReplayHandler<T> {
    /**
     * Handle a replayed message.
     *
     * @param message the message being replayed
     * @param isReplay true since this is always a replay
     */
    void handleReplayMessage(T message, boolean isReplay);
  }

  private void discardPreviousHeightMessages() {
    if (!buffer.isEmpty()) {
      for (long h = buffer.firstKey(); h <= chainHeight; h++) {
        buffer.remove(h);
      }
    }
  }

  /**
   * Total messages size.
   *
   * @return the long
   */
  @VisibleForTesting
  long totalMessagesSize() {
    return buffer.values().stream().map(List::size).reduce(0, Integer::sum).longValue();
  }
}
