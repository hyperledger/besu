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
package tech.pegasys.pantheon.util;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.util.RawBlockIterator;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.Logger;

/** Pantheon Block Import Util. */
public class BlockImporter {
  private static final Logger LOG = getLogger();
  /**
   * Imports blocks that are stored as concatenated RLP sections in the given file into Pantheon's
   * block storage.
   *
   * @param blocks Path to the file containing the blocks
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param <C> the consensus context type
   * @param <M> the type of miner being used within the executing pantheon
   * @return the import result
   * @throws IOException On Failure
   */
  public <C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
      BlockImporter.ImportResult importBlockchain(
          final Path blocks, final PantheonController<C> pantheonController) throws IOException {
    final ProtocolSchedule<C> protocolSchedule = pantheonController.getProtocolSchedule();
    final ProtocolContext<C> context = pantheonController.getProtocolContext();
    final GenesisConfig<C> genesis = pantheonController.getGenesisConfig();

    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            blocks,
            rlp ->
                BlockHeader.readFrom(
                    rlp, ScheduleBasedBlockHashFunction.create(protocolSchedule)))) {
      final MutableBlockchain blockchain = context.getBlockchain();
      int count = 1;
      BlockHeader previousHeader = null;
      while (iterator.hasNext()) {
        final Block block = iterator.next();
        final BlockHeader header = block.getHeader();
        if (header.getNumber() == genesis.getBlock().getHeader().getNumber()) {
          continue;
        }
        if (header.getNumber() % 100 == 0) {
          LOG.info("Import at block {}", header.getNumber());
        }
        if (blockchain.contains(header.getHash())) {
          continue;
        }
        if (previousHeader == null) {
          previousHeader = lookupPreviousHeader(blockchain, header);
        }
        final ProtocolSpec<C> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
        final BlockHeaderValidator<C> blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
        final boolean validHeader =
            blockHeaderValidator.validateHeader(
                header, previousHeader, context, HeaderValidationMode.FULL);
        if (!validHeader) {
          throw new IllegalStateException(
              "Invalid header at block number " + header.getNumber() + ".");
        }
        final tech.pegasys.pantheon.ethereum.core.BlockImporter<C> blockImporter =
            protocolSpec.getBlockImporter();
        final boolean blockImported =
            blockImporter.importBlock(context, block, HeaderValidationMode.NONE);
        if (!blockImported) {
          throw new IllegalStateException(
              "Invalid block at block number " + header.getNumber() + ".");
        }
        ++count;
        previousHeader = header;
      }
      return new BlockImporter.ImportResult(blockchain.getChainHead().getTotalDifficulty(), count);
    } finally {
      pantheonController.close();
    }
  }

  private BlockHeader lookupPreviousHeader(
      final MutableBlockchain blockchain, final BlockHeader header) {
    return blockchain
        .getBlockHeader(header.getParentHash())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Block %s does not connect to the existing chain. Current chain head %s",
                        header.getNumber(), blockchain.getChainHeadBlockNumber())));
  }

  public static final class ImportResult {

    public final UInt256 td;

    public final int count;

    ImportResult(final UInt256 td, final int count) {
      this.td = td;
      this.count = count;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("td", td).add("count", count).toString();
    }
  }
}
