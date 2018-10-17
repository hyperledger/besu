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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;

import java.util.List;

/**
 * The IBFT BlockImporter implementation. Adds votes to VoteTally as blocks are added to the chain.
 */
public class IbftBlockImporter implements BlockImporter<IbftContext> {

  private final BlockImporter<IbftContext> delegate;
  private final VoteTallyUpdater voteTallyUpdater;

  public IbftBlockImporter(
      final BlockImporter<IbftContext> delegate, final VoteTallyUpdater voteTallyUpdater) {
    this.delegate = delegate;
    this.voteTallyUpdater = voteTallyUpdater;
  }

  @Override
  public boolean importBlock(
      final ProtocolContext<IbftContext> context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final boolean result =
        delegate.importBlock(context, block, headerValidationMode, ommerValidationMode);
    updateVoteTally(result, block.getHeader(), context);
    return result;
  }

  @Override
  public boolean fastImportBlock(
      final ProtocolContext<IbftContext> context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode) {
    final boolean result = delegate.fastImportBlock(context, block, receipts, headerValidationMode);
    updateVoteTally(result, block.getHeader(), context);
    return result;
  }

  private void updateVoteTally(
      final boolean result, final BlockHeader header, final ProtocolContext<IbftContext> context) {
    if (result) {
      voteTallyUpdater.updateForBlock(header, context.getConsensusState().getVoteTally());
    }
  }
}
