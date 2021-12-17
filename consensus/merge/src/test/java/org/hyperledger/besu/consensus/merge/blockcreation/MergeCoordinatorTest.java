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

import org.hyperledger.besu.config.experimental.MergeOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardsSyncContext;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeCoordinatorTest implements MergeGenesisConfigHelper {

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
}
