/*
 * Copyright Hyperledger Besu Contributors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeUnfinalizedValidationRule implements AttachedBlockHeaderValidationRule {
  private static final Logger LOG = LoggerFactory.getLogger(MergeUnfinalizedValidationRule.class);

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {

    MergeContext mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    // if we have a finalized blockheader, fail this rule if
    // the block number is lower than finalized
    // or block number is the same but hash is different
    if (mergeContext
        .getFinalized()
        .filter(finalized -> header.getNumber() <= finalized.getNumber())
        .filter(finalized -> !header.getHash().equals(finalized.getHash()))
        .isPresent()) {
      LOG.warn(
          "BlockHeader {} failed validation due to block {} already finalized",
          header.toLogString(),
          mergeContext.getFinalized().map(BlockHeader::toLogString).orElse("{}"));
      return false;
    }

    return true;
  }
}
