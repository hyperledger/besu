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
package org.hyperledger.besu.consensus.merge.headervalidationrules;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MergeUnfinalizedValidationRule implements AttachedBlockHeaderValidationRule {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {

    // TODO: see if there is a more appropriate mechanism to enforce this at block import time.
    //       Otherwise if we validate existing blocks that are already finalized then we are
    //       going to unnecessarily fail them.

    MergeContext mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    if (header.getNumber() <= mergeContext.getFinalized()) {
      LOG.warn("BlockHeader failed validation due to block number already finalized");
      return false;
    }

    return true;
  }
}
