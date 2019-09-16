/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FullImportBlockStep<C> implements Consumer<Block> {
  private static final Logger LOG = LogManager.getLogger();
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;

  public FullImportBlockStep(
      final ProtocolSchedule<C> protocolSchedule, final ProtocolContext<C> protocolContext) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public void accept(final Block block) {
    final long blockNumber = block.getHeader().getNumber();
    final BlockImporter<C> importer =
        protocolSchedule.getByBlockNumber(blockNumber).getBlockImporter();
    if (!importer.importBlock(protocolContext, block, HeaderValidationMode.SKIP_DETACHED)) {
      throw new InvalidBlockException("Failed to import block", blockNumber, block.getHash());
    }
    if (blockNumber % 200 == 0) {
      LOG.info("Import reached block {}", blockNumber);
    }
  }
}
