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

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for ensuring the extra data fields in the header contain the appropriate number of
 * bytes.
 */
public class ExtraDataMaxLengthValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(ExtraDataMaxLengthValidationRule.class);
  private final long maxExtraDataBytes;

  public ExtraDataMaxLengthValidationRule(final long maxExtraDataBytes) {
    this.maxExtraDataBytes = maxExtraDataBytes;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateExtraData(header.getExtraData());
  }

  private boolean validateExtraData(final Bytes extraData) {
    if (extraData.size() > maxExtraDataBytes) {
      LOG.info(
          "Invalid block header: extra data field length {} is greater {}",
          extraData.size(),
          maxExtraDataBytes);
      return false;
    }

    return true;
  }
}
