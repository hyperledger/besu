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
package org.hyperledger.besu.config;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;

public class BftConfigOptions {

  public static final BftConfigOptions DEFAULT =
      new BftConfigOptions(JsonUtil.createEmptyObjectNode());

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

  private final ObjectNode bftConfigRoot;

  BftConfigOptions(final ObjectNode bftConfigRoot) {
    this.bftConfigRoot = bftConfigRoot;
  }

  public long getEpochLength() {
    return JsonUtil.getLong(bftConfigRoot, "epochlength", DEFAULT_EPOCH_LENGTH);
  }

  public int getBlockPeriodSeconds() {
    return JsonUtil.getInt(bftConfigRoot, "blockperiodseconds", DEFAULT_BLOCK_PERIOD_SECONDS);
  }

  public int getRequestTimeoutSeconds() {
    return JsonUtil.getInt(bftConfigRoot, "requesttimeoutseconds", DEFAULT_ROUND_EXPIRY_SECONDS);
  }

  public int getGossipedHistoryLimit() {
    return JsonUtil.getInt(bftConfigRoot, "gossipedhistorylimit", DEFAULT_GOSSIPED_HISTORY_LIMIT);
  }

  public int getMessageQueueLimit() {
    return JsonUtil.getInt(bftConfigRoot, "messagequeuelimit", DEFAULT_MESSAGE_QUEUE_LIMIT);
  }

  public int getDuplicateMessageLimit() {
    return JsonUtil.getInt(bftConfigRoot, "duplicatemessagelimit", DEFAULT_DUPLICATE_MESSAGE_LIMIT);
  }

  public int getFutureMessagesLimit() {
    return JsonUtil.getInt(bftConfigRoot, "futuremessageslimit", DEFAULT_FUTURE_MESSAGES_LIMIT);
  }

  public int getFutureMessagesMaxDistance() {
    return JsonUtil.getInt(
        bftConfigRoot, "futuremessagesmaxdistance", DEFAULT_FUTURE_MESSAGES_MAX_DISTANCE);
  }

  public Optional<String> getMiningBeneficiary() {
    return JsonUtil.getString(bftConfigRoot, "miningbeneficiary");
  }

  public BigInteger getBlockRewardWei() {
    final Optional<String> configFileContent = JsonUtil.getString(bftConfigRoot, "blockreward");

    if (configFileContent.isEmpty()) {
      return BigInteger.ZERO;
    }
    final String weiStr = configFileContent.get();
    if (weiStr.startsWith("0x")) {
      return new BigInteger(1, Bytes.fromHexStringLenient(weiStr).toArrayUnsafe());
    }
    return new BigInteger(weiStr);
  }

  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    if (bftConfigRoot.has("epochlength")) {
      builder.put("epochLength", getEpochLength());
    }
    if (bftConfigRoot.has("blockperiodseconds")) {
      builder.put("blockPeriodSeconds", getBlockPeriodSeconds());
    }
    if (bftConfigRoot.has("requesttimeoutseconds")) {
      builder.put("requestTimeoutSeconds", getRequestTimeoutSeconds());
    }
    if (bftConfigRoot.has("gossipedhistorylimit")) {
      builder.put("gossipedHistoryLimit", getGossipedHistoryLimit());
    }
    if (bftConfigRoot.has("messagequeuelimit")) {
      builder.put("messageQueueLimit", getMessageQueueLimit());
    }
    if (bftConfigRoot.has("duplicatemessagelimit")) {
      builder.put("duplicateMessageLimit", getDuplicateMessageLimit());
    }
    if (bftConfigRoot.has("futuremessageslimit")) {
      builder.put("futureMessagesLimit", getFutureMessagesLimit());
    }
    if (bftConfigRoot.has("futuremessagesmaxdistance")) {
      builder.put("futureMessagesMaxDistance", getFutureMessagesMaxDistance());
    }
    return builder.build();
  }
}
