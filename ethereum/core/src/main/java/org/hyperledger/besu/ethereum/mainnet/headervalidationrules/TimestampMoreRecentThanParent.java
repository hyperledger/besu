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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Responsible for ensuring the timestamp of a block is newer than its parent. */
public class TimestampMoreRecentThanParent implements DetachedBlockHeaderValidationRule {

  private final Logger LOG = LogManager.getLogger();
  private final long minimumSecondsSinceParent;

  public TimestampMoreRecentThanParent(final long minimumSecondsSinceParent) {
    checkArgument(minimumSecondsSinceParent >= 0, "minimumSecondsSinceParent must be positive");
    this.minimumSecondsSinceParent = minimumSecondsSinceParent;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateTimestamp(header.getTimestamp(), parent.getTimestamp());
  }

  private boolean validateTimestamp(final long timestamp, final long parentTimestamp) {
    return validateHeaderSufficientlyAheadOfParent(timestamp, parentTimestamp);
  }

  private boolean validateHeaderSufficientlyAheadOfParent(
      final long timestamp, final long parentTimestamp) {
    if ((timestamp - minimumSecondsSinceParent) < parentTimestamp) {
      LOG.trace(
          "Invalid block header: timestamp {} is not sufficiently newer than parent timestamp {}",
          timestamp,
          parentTimestamp);
      return false;
    }

    return true;
  }
}
