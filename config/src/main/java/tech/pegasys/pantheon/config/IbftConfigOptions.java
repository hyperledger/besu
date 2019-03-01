/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.config;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.json.JsonObject;

public class IbftConfigOptions {

  public static final IbftConfigOptions DEFAULT = new IbftConfigOptions(new JsonObject());

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 1;
  private static final int DEFAULT_ROUND_EXPIRY_SECONDS = 1;
  // In a healthy network this can be very small. This default limit will allow for suitable
  // protection for on a typical 20 node validator network with multiple rounds
  private static final int DEFAULT_GOSSIPED_HISTORY_LIMIT = 1000;
  private static final int DEFAULT_MESSAGE_QUEUE_LIMIT = 1000;
  private static final int DEFAULT_DUPLICATE_MESSAGE_LIMIT = 100;
  private static final int DEFAULT_FUTURE_MESSAGES_LIMIT = 1000;
  private static final int DEFAULT_FUTURE_MESSAGES_MAX_DISTANCE = 10;

  private final JsonObject ibftConfigRoot;

  IbftConfigOptions(final JsonObject ibftConfigRoot) {
    this.ibftConfigRoot = ibftConfigRoot;
  }

  public long getEpochLength() {
    return ibftConfigRoot.getLong("epochlength", DEFAULT_EPOCH_LENGTH);
  }

  public int getBlockPeriodSeconds() {
    return ibftConfigRoot.getInteger("blockperiodseconds", DEFAULT_BLOCK_PERIOD_SECONDS);
  }

  public int getRequestTimeoutSeconds() {
    return ibftConfigRoot.getInteger("requesttimeoutseconds", DEFAULT_ROUND_EXPIRY_SECONDS);
  }

  public int getGossipedHistoryLimit() {
    return ibftConfigRoot.getInteger("gossipedhistorylimit", DEFAULT_GOSSIPED_HISTORY_LIMIT);
  }

  public int getMessageQueueLimit() {
    return ibftConfigRoot.getInteger("messagequeuelimit", DEFAULT_MESSAGE_QUEUE_LIMIT);
  }

  public int getDuplicateMessageLimit() {
    return ibftConfigRoot.getInteger("duplicatemessagelimit", DEFAULT_DUPLICATE_MESSAGE_LIMIT);
  }

  public int getFutureMessagesLimit() {
    return ibftConfigRoot.getInteger("futuremessageslimit", DEFAULT_FUTURE_MESSAGES_LIMIT);
  }

  public int getFutureMessagesMaxDistance() {
    return ibftConfigRoot.getInteger(
        "futuremessagesmaxdistance", DEFAULT_FUTURE_MESSAGES_MAX_DISTANCE);
  }

  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    if (ibftConfigRoot.containsKey("epochlength")) {
      builder.put("epochLength", getEpochLength());
    }
    if (ibftConfigRoot.containsKey("blockperiodseconds")) {
      builder.put("blockPeriodSeconds", getBlockPeriodSeconds());
    }
    if (ibftConfigRoot.containsKey("requesttimeoutseconds")) {
      builder.put("requestTimeoutSeconds", getRequestTimeoutSeconds());
    }
    if (ibftConfigRoot.containsKey("gossipedhistorylimit")) {
      builder.put("gossipedHistoryLimit", getGossipedHistoryLimit());
    }
    if (ibftConfigRoot.containsKey("messagequeuelimit")) {
      builder.put("messageQueueLimit", getMessageQueueLimit());
    }
    if (ibftConfigRoot.containsKey("duplicatemessagelimit")) {
      builder.put("duplicateMessageLimit", getDuplicateMessageLimit());
    }
    if (ibftConfigRoot.containsKey("futuremessageslimit")) {
      builder.put("futureMessagesLimit", getFutureMessagesLimit());
    }
    if (ibftConfigRoot.containsKey("futuremessagesmaxdistance")) {
      builder.put("futureMessagesMaxDistance", getFutureMessagesMaxDistance());
    }
    return builder.build();
  }
}
