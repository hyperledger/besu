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
package tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the timestamp of a block is not more than "acceptableClockDriftSeconds'
 * into the future.
 */
public class TimestampBoundedByFutureParameter implements DetachedBlockHeaderValidationRule {

  private final Logger LOG = LogManager.getLogger();
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
      LOG.trace(
          "Invalid block header: timestamp {} is greater than the timestamp margin {}",
          timestamp,
          timestampMargin);
      return false;
    }
    return true;
  }
}
