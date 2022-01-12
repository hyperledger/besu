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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures the hash of the parent block matches that specified in the parent hash of the proposed
 * header.
 */
public class AncestryValidationRule implements DetachedBlockHeaderValidationRule {
  private static final Logger LOG = LoggerFactory.getLogger(AncestryValidationRule.class);

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (!header.getParentHash().equals(parent.getHash())) {
      LOG.info(
          "Invalid block header:  Parent hash {} does not match " + "supplied parent header {}.",
          header.getParentHash(),
          parent.getHash());
      return false;
    }

    if (header.getNumber() != (parent.getNumber() + 1)) {
      LOG.info(
          "Invalid block header: number {} is not one more than parent number {}",
          header.getNumber(),
          parent.getNumber());
      return false;
    }

    return true;
  }
}
