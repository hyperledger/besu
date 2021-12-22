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

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.config.experimental.MergeOptions;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class MergeReorgTest implements MergeGenesisConfigHelper {

  @Mock AbstractPendingTransactionsSorter mockSorter;

  private MergeCoordinator coordinator;

  private final MergeContext mergeContext = PostMergeContext.get();
  private final ProtocolSchedule mockProtocolSchedule = getMergeProtocolSchedule();
  private final GenesisState genesisState =
      GenesisState.fromConfig(getGenesisConfigFile(), mockProtocolSchedule);

  private final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());

  private final ProtocolContext protocolContext =
      new ProtocolContext(blockchain, worldStateArchive, mergeContext);

  private final Address suggestedFeeRecipient = Address.ZERO;
  private final Address coinbase = genesisAllocations().findFirst().get();
  private final BlockHeaderTestFixture headerGenerator = new BlockHeaderTestFixture();
  private final BaseFeeMarket feeMarket =
      new LondonFeeMarket(0, genesisState.getBlock().getHeader().getBaseFee());

  @Before
  public void setUp() {
    var mutable = worldStateArchive.getMutable();
    genesisState.writeStateTo(mutable);
    mutable.persist(null);

    MergeOptions.setMergeEnabled(true);
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

  /* as long as a post-merge PoS block has not been finalized,
  then yuo can and should be able to re-org to a different pre-TTD block
  say there is viable TTD block A and B, then we can have a PoS chain build on A for a while
      and then see another PoS chain build on B that has a higher fork choice weight and causes a re-org
  once any post-merge PoS chain is finalied though, you'd never re-org any PoW blocks in the tree ever again */

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
