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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the extra data fields in the header contain the appropriate number of
 * bytes.
 */
public class ExtraDataMaxLengthValidationRule implements DetachedBlockHeaderValidationRule {

  private final Logger LOG = LogManager.getLogger(ExtraDataMaxLengthValidationRule.class);
  private final long maxExtraDataBytes;

  public ExtraDataMaxLengthValidationRule(final long maxExtraDataBytes) {
    this.maxExtraDataBytes = maxExtraDataBytes;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateExtraData(header.getExtraData());
  }

  private boolean validateExtraData(final BytesValue extraData) {
    if (extraData.size() > maxExtraDataBytes) {
      LOG.trace(
          "Invalid block header: extra data field length {} is greater {}",
          extraData.size(),
          maxExtraDataBytes);
      return false;
    }

    return true;
  }
}
