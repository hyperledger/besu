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

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

/** The Ibft legacy config options. */
public class IbftLegacyConfigOptions {

  /** The constant DEFAULT. */
  public static final IbftLegacyConfigOptions DEFAULT =
      new IbftLegacyConfigOptions(JsonUtil.createEmptyObjectNode());

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 1;
  private static final int DEFAULT_ROUND_EXPIRY_SECONDS = 1;
  private static final long DEFAULT_CEIL_2N_BY_3_BLOCK = 0L;

  private final ObjectNode ibftConfigRoot;

  /**
   * Instantiates a new Ibft legacy config options.
   *
   * @param ibftConfigRoot the ibft config root
   */
  IbftLegacyConfigOptions(final ObjectNode ibftConfigRoot) {
    this.ibftConfigRoot = ibftConfigRoot;
  }

  /**
   * Gets epoch length.
   *
   * @return the epoch length
   */
  public long getEpochLength() {
    return JsonUtil.getLong(ibftConfigRoot, "epochlength", DEFAULT_EPOCH_LENGTH);
  }

  /**
   * Gets block period seconds.
   *
   * @return the block period seconds
   */
  public int getBlockPeriodSeconds() {
    return JsonUtil.getPositiveInt(
        ibftConfigRoot, "blockperiodseconds", DEFAULT_BLOCK_PERIOD_SECONDS);
  }

  /**
   * Gets request timeout seconds.
   *
   * @return the request timeout seconds
   */
  public int getRequestTimeoutSeconds() {
    return JsonUtil.getInt(ibftConfigRoot, "requesttimeoutseconds", DEFAULT_ROUND_EXPIRY_SECONDS);
  }

  /**
   * Gets ceil 2N by 3 block.
   *
   * @return the ceil 2N by 3 block
   */
  public long getCeil2Nby3Block() {
    return JsonUtil.getLong(ibftConfigRoot, "ceil2nby3block", DEFAULT_CEIL_2N_BY_3_BLOCK);
  }

  /**
   * As map.
   *
   * @return the map
   */
  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    if (ibftConfigRoot.has("epochlength")) {
      builder.put("epochLength", getEpochLength());
    }
    if (ibftConfigRoot.has("blockperiodseconds")) {
      builder.put("blockPeriodSeconds", getBlockPeriodSeconds());
    }
    if (ibftConfigRoot.has("requesttimeoutseconds")) {
      builder.put("requestTimeoutSeconds", getRequestTimeoutSeconds());
    }
    if (ibftConfigRoot.has("ceil2nby3block")) {
      builder.put("ceil2nby3block", getCeil2Nby3Block());
    }

    return builder.build();
  }
}
