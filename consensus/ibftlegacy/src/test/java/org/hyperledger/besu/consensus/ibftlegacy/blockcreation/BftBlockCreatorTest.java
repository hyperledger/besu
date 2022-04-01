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
package org.hyperledger.besu.consensus.ibftlegacy.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibftlegacy.IbftLegacyContextBuilder.setupContextWithValidators;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.ibftlegacy.IbftBlockHeaderValidationRulesetFactory;
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraData;
import org.hyperledger.besu.consensus.ibftlegacy.IbftProtocolSchedule;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class BftBlockCreatorTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void headerProducedPassesValidationRules() {
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

    final KeyPair nodeKeys = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    // Add the local node as a validator (can't propose a block if node is not a validator).
    final Address localAddr = Address.extract(Hash.hash(nodeKeys.getPublicKey().getEncodedBytes()));
    final List<Address> initialValidatorList =
        Arrays.asList(
            Address.fromHexString(String.format("%020d", 1)),
            Address.fromHexString(String.format("%020d", 2)),
            Address.fromHexString(String.format("%020d", 3)),
            Address.fromHexString(String.format("%020d", 4)),
            localAddr);

    final ProtocolSchedule protocolSchedule =
        IbftProtocolSchedule.create(
            GenesisConfigFile.fromConfig("{\"config\": {\"spuriousDragonBlock\":0}}")
                .getConfigOptions(),
            false,
            EvmConfiguration.DEFAULT);
    final ProtocolContext protContext =
        new ProtocolContext(
            blockchain,
            createInMemoryWorldStateArchive(),
            setupContextWithValidators(initialValidatorList));

    final IbftBlockCreator blockCreator =
        new IbftBlockCreator(
            Address.fromHexString(String.format("%020d", 0)),
            () -> Optional.of(10_000_000L),
            parent ->
                new IbftExtraData(
                        Bytes.wrap(new byte[32]), Lists.newArrayList(), null, initialValidatorList)
                    .encode(),
            new GasPricePendingTransactionsSorter(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                1,
                TestClock.fixed(),
                metricsSystem,
                blockchain::getChainHeadHeader,
                TransactionPoolConfiguration.DEFAULT_PRICE_BUMP),
            protContext,
            protocolSchedule,
            nodeKeys,
            Wei.ZERO,
            0.8,
            parentHeader);

    final Block block = blockCreator.createBlock(Instant.now().getEpochSecond());

    final BlockHeaderValidator rules =
        IbftBlockHeaderValidationRulesetFactory.ibftProposedBlockValidator(0).build();

    final boolean validationResult =
        rules.validateHeader(
            block.getHeader(), parentHeader, protContext, HeaderValidationMode.FULL);

    assertThat(validationResult).isTrue();
  }
}
