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
package org.hyperledger.besu.consensus.ibft.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BaseBftProtocolSchedule;
import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreator;
import org.hyperledger.besu.consensus.ibft.IbftBlockHeaderValidationRulesetFactory;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class BftBlockCreatorTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void createdBlockPassesValidationRulesAndHasAppropriateHashAndMixHash() {
    // Construct a parent block.
    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.gasLimit(5000); // required to pass validation rule checks.
    final BlockHeader parentHeader = blockHeaderBuilder.buildHeader();
    final Optional<BlockHeader> optionalHeader = Optional.of(parentHeader);

    // Construct a block chain and world state
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    when(blockchain.getChainHeadHash()).thenReturn(parentHeader.getHash());
    when(blockchain.getBlockHeader(any())).thenReturn(optionalHeader);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final List<Address> initialValidatorList = Lists.newArrayList();
    for (int i = 0; i < 4; i++) {
      initialValidatorList.add(AddressHelpers.ofValue(i));
    }

    final IbftExtraDataCodec bftExtraDataEncoder = new IbftExtraDataCodec();

    final BaseBftProtocolSchedule bftProtocolSchedule =
        new BaseBftProtocolSchedule() {
          @Override
          public BlockHeaderValidator.Builder createBlockHeaderRuleset(
              final BftConfigOptions config, final FeeMarket feeMarket) {
            return IbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
                5, Optional.empty());
          }
        };
    final GenesisConfigOptions configOptions =
        GenesisConfigFile.fromConfig("{\"config\": {\"spuriousDragonBlock\":0}}")
            .getConfigOptions();
    final ForksSchedule<BftConfigOptions> forksSchedule =
        new ForksSchedule<>(List.of(new ForkSpec<>(0, configOptions.getBftConfigOptions())));
    final ProtocolSchedule protocolSchedule =
        bftProtocolSchedule.createProtocolSchedule(
            configOptions,
            forksSchedule,
            PrivacyParameters.DEFAULT,
            false,
            bftExtraDataEncoder,
            EvmConfiguration.DEFAULT);
    final ProtocolContext protContext =
        new ProtocolContext(
            blockchain,
            createInMemoryWorldStateArchive(),
            setupContextWithBftExtraDataEncoder(initialValidatorList, bftExtraDataEncoder));

    final GasPricePendingTransactionsSorter pendingTransactions =
        new GasPricePendingTransactionsSorter(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem,
            blockchain::getChainHeadHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

    final BftBlockCreator blockCreator =
        new BftBlockCreator(
            forksSchedule,
            initialValidatorList.get(0),
            () -> Optional.of(10_000_000L),
            parent ->
                bftExtraDataEncoder.encode(
                    new BftExtraData(
                        Bytes.wrap(new byte[32]),
                        Collections.emptyList(),
                        Optional.empty(),
                        0,
                        initialValidatorList)),
            pendingTransactions,
            protContext,
            protocolSchedule,
            Wei.ZERO,
            0.8,
            parentHeader,
            bftExtraDataEncoder);

    final int secondsBetweenBlocks = 1;
    final Block block = blockCreator.createBlock(parentHeader.getTimestamp() + 1);

    final BlockHeaderValidator rules =
        IbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
                secondsBetweenBlocks, Optional.empty())
            .build();

    // NOTE: The header will not contain commit seals, so can only do light validation on header.
    final boolean validationResult =
        rules.validateHeader(
            block.getHeader(), parentHeader, protContext, HeaderValidationMode.LIGHT);

    assertThat(validationResult).isTrue();

    final BlockHeader header = block.getHeader();
    final BftExtraData extraData = bftExtraDataEncoder.decode(header);
    assertThat(block.getHash())
        .isEqualTo(
            new BftBlockHashing(bftExtraDataEncoder)
                .calculateDataHashForCommittedSeal(header, extraData));
  }
}
