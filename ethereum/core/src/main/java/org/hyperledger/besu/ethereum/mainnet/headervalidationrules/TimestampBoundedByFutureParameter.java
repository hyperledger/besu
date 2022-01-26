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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for ensuring the timestamp of a block is not more than "acceptableClockDriftSeconds'
 * into the future.
 */
public class TimestampBoundedByFutureParameter implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG =
      LoggerFactory.getLogger(TimestampBoundedByFutureParameter.class);
  private final long acceptableClockDriftSeconds;

  public TimestampBoundedByFutureParameter(final long acceptableClockDriftSeconds) {
    this.acceptableClockDriftSeconds = acceptableClockDriftSeconds;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateTimestamp(header.getTimestamp());
  }

  private boolean validateTimestamp(final long timestamp) {
    return validateHeaderNotAheadOfCurrentSystemTime(timestamp);
  }

  private boolean validateHeaderNotAheadOfCurrentSystemTime(final long timestamp) {
    final long timestampMargin =
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            + acceptableClockDriftSeconds;
    if (Long.compareUnsigned(timestamp, timestampMargin) > 0) {
      LOG.info(
          "Invalid block header: timestamp {} is greater than the timestamp margin {}",
          timestamp,
          timestampMargin);
      return false;
    }
    return true;
  }
}
