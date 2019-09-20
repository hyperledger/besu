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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import java.time.Duration;
import java.time.Instant;

import com.google.common.annotations.VisibleForTesting;

abstract class Filter {

  private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofMinutes(10);

  private final String id;
  private Instant expireTime;

  Filter(final String id) {
    this.id = id;
    resetExpireTime();
  }

  String getId() {
    return id;
  }

  void resetExpireTime() {
    this.expireTime = Instant.now().plus(DEFAULT_EXPIRE_DURATION);
  }

  boolean isExpired() {
    return Instant.now().isAfter(expireTime);
  }

  @VisibleForTesting
  void setExpireTime(final Instant expireTime) {
    this.expireTime = expireTime;
  }

  @VisibleForTesting
  Instant getExpireTime() {
    return expireTime;
  }
}
