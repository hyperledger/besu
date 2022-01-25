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
package org.hyperledger.besu.consensus.common.bft.headervalidationrules;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BftVanityDataValidationRule implements AttachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(BftVanityDataValidationRule.class);

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {
    final BftContext bftContext = protocolContext.getConsensusContext(BftContext.class);
    final BftExtraData bftExtraData = bftContext.getBlockInterface().getExtraData(header);

    if (bftExtraData.getVanityData().size() != BftExtraDataCodec.EXTRA_VANITY_LENGTH) {
      LOG.info("Invalid block header: Bft Extra Data does not contain 32 bytes of vanity data.");
      return false;
    }
    return true;
  }
}
