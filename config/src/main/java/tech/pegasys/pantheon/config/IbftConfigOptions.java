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

import io.vertx.core.json.JsonObject;

public class IbftConfigOptions {

  public static final IbftConfigOptions DEFAULT = new IbftConfigOptions(new JsonObject());

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 1;
  private static final int DEFAULT_ROUND_EXPIRY_SECONDS = 1;

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
}
