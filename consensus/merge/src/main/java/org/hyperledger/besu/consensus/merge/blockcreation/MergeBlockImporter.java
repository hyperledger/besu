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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MergeBlockImporter extends MainnetBlockImporter {
  private static final Logger LOG = LogManager.getLogger();

  public MergeBlockImporter(final BlockValidator blockValidator) {
    super(blockValidator);
  }

  @Override
  public synchronized boolean importBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    if (context.getConsensusContext(MergeContext.class).isPostMerge()) {
      LOG.info("should be a no-op import for block {}", block.getHeader().getNumber());
      // TODO: for now optimistically import, this should only come from initial sync pre-TTD
      // return true;
    }
    LOG.info("Imported block {}", block.getHeader().getNumber());
    return super.importBlock(context, block, headerValidationMode, ommerValidationMode);
  }
}
