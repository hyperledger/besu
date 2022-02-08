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
package org.hyperledger.besu.consensus.merge.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.config.experimental.MergeConfiguration;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardsSyncContext;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeCoordinatorTest implements MergeGenesisConfigHelper {

  @Mock AbstractPendingTransactionsSorter mockSorter;

  private MergeCoordinator coordinator;

  private final MergeContext mergeContext = PostMergeContext.get();
  private final ProtocolSchedule mockProtocolSchedule = getMergeProtocolSchedule();
  private final GenesisState genesisState =
      GenesisState.fromConfig(getPosGenesisConfigFile(), mockProtocolSchedule);

  private final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());

  private final ProtocolContext protocolContext =
      new ProtocolContext(blockchain, worldStateArchive, mergeContext);

  private final Address suggestedFeeRecipient = Address.ZERO;
  private final Address coinbase = genesisAllocations(getPosGenesisConfigFile()).findFirst().get();
  private final BlockHeaderTestFixture headerGenerator = new BlockHeaderTestFixture();
  private final BaseFeeMarket feeMarket =
      new LondonFeeMarket(0, genesisState.getBlock().getHeader().getBaseFee());

  @Before
  public void setUp() {
    var mutable = worldStateArchive.getMutable();
    genesisState.writeStateTo(mutable);
    mutable.persist(null);

    MergeConfiguration.setMergeEnabled(true);
    this.coordinator =
        new MergeCoordinator(
            protocolContext,
            mockProtocolSchedule,
            mockSorter,
            new MiningParameters.Builder().coinbase(coinbase).build(),
            mock(BackwardsSyncContext.class));
    mergeContext.setTerminalTotalDifficulty(
        genesisState.getBlock().getHeader().getDifficulty().plus(1L));
    mergeContext.setIsPostMerge(genesisState.getBlock().getHeader().getDifficulty().plus(2L));
  }

  @Test
  public void coinbaseShouldMatchSuggestedFeeRecipient() {
    var payloadId =
        coordinator.preparePayload(
            genesisState.getBlock().getHeader(),
            System.currentTimeMillis() / 1000,
            Bytes32.ZERO,
            suggestedFeeRecipient);

    var mockBlock = mergeContext.retrieveBlockById(payloadId);
    assertThat(mockBlock).isPresent();
    Block proposed = mockBlock.get();
    assertThat(proposed.getHeader().getCoinbase()).isEqualTo(suggestedFeeRecipient);
  }

  @Test
  public void latestValidAncestorDescendsFromTerminal() {

    BlockHeader terminalHeader = terminalPowBlock();
    coordinator.executeBlock(new Block(terminalHeader, BlockBody.empty()));

    BlockHeader parentHeader =
        headerGenerator
            .difficulty(Difficulty.ZERO)
            .parentHash(terminalHeader.getHash())
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .number(terminalHeader.getNumber() + 1)
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    terminalHeader.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .buildHeader();

    Block parent = new Block(parentHeader, BlockBody.empty());
    coordinator.executeBlock(parent);

    BlockHeader childHeader =
        headerGenerator
            .difficulty(Difficulty.ZERO)
            .parentHash(parentHeader.getHash())
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .number(parentHeader.getNumber() + 1)
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    parentHeader.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .buildHeader();
    Block child = new Block(childHeader, BlockBody.empty());
    coordinator.executeBlock(child);
    assertThat(this.coordinator.latestValidAncestorDescendsFromTerminal(child.getHeader()))
        .isTrue();
  }

  @Test
  public void latestValidAncestorDescendsFromFinalizedBlock() {

    BlockHeader terminalHeader = terminalPowBlock();
    coordinator.executeBlock(new Block(terminalHeader, BlockBody.empty()));

    BlockHeader grandParentHeader =
        headerGenerator
            .difficulty(Difficulty.ZERO)
            .parentHash(terminalHeader.getHash())
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .number(terminalHeader.getNumber() + 1)
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    terminalHeader.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .buildHeader();

    Block grandParent = new Block(grandParentHeader, BlockBody.empty());
    coordinator.executeBlock(grandParent);
    mergeContext.setFinalized(grandParentHeader);

    MergeContext spy = spy(this.mergeContext);
    BlockHeader parentHeader =
        headerGenerator
            .difficulty(Difficulty.ZERO)
            .parentHash(grandParentHeader.getHash())
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .number(grandParentHeader.getNumber() + 1)
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    grandParentHeader.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .buildHeader();

    Block parent = new Block(parentHeader, BlockBody.empty());
    coordinator.executeBlock(parent);

    BlockHeader childHeader =
        headerGenerator
            .difficulty(Difficulty.ZERO)
            .parentHash(parentHeader.getHash())
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .number(parentHeader.getNumber() + 1)
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    parentHeader.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .buildHeader();
    Block child = new Block(childHeader, BlockBody.empty());
    coordinator.executeBlock(child);
    assertThat(this.coordinator.latestValidAncestorDescendsFromTerminal(child.getHeader()))
        .isTrue();
    Mockito.verify(spy, never()).getTerminalPoWBlock();
  }

  private BlockHeader terminalPowBlock() {

    BlockHeader terminal =
        headerGenerator
            .difficulty(Difficulty.MAX_VALUE)
            .parentHash(genesisState.getBlock().getHash())
            .number(genesisState.getBlock().getHeader().getNumber() + 1)
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    genesisState.getBlock().getHeader().getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .gasLimit(genesisState.getBlock().getHeader().getGasLimit())
            .stateRoot(genesisState.getBlock().getHeader().getStateRoot())
            .buildHeader();
    mergeContext.setTerminalPoWBlock(Optional.of(terminal));
    return terminal;
  }
}
