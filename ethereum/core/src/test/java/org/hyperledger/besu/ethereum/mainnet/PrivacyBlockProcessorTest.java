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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_BASE64_ENCLAVE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacyBlockProcessorTest {

  private PrivacyBlockProcessor privacyBlockProcessor;
  private PrivateStateStorage privateStateStorage;
  private AbstractBlockProcessor blockProcessor;
  private WorldStateArchive privateWorldStateArchive;
  private Enclave enclave;
  private ProtocolSchedule protocolSchedule;
  private WorldStateArchive publicWorldStateArchive;

  @Before
  public void setUp() {
    blockProcessor = mock(AbstractBlockProcessor.class);
    privateStateStorage = new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
    privateWorldStateArchive = mock(WorldStateArchive.class);
    enclave = mock(Enclave.class);
    protocolSchedule = mock(ProtocolSchedule.class);
    this.privacyBlockProcessor =
        new PrivacyBlockProcessor(
            blockProcessor,
            protocolSchedule,
            enclave,
            privateStateStorage,
            privateWorldStateArchive,
            new PrivateStateRootResolver(privateStateStorage),
            new PrivateStateGenesisAllocator(
                true, (privacyGroupId, blockNumber) -> Collections::emptyList));
    publicWorldStateArchive = mock(WorldStateArchive.class);
    privacyBlockProcessor.setPublicWorldStateArchive(publicWorldStateArchive);
  }

  @Test
  public void mustCopyPreviousPrivacyGroupBlockHeadMap() {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final Blockchain blockchain = mock(Blockchain.class);
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    final PrivacyGroupHeadBlockMap expected =
        new PrivacyGroupHeadBlockMap(Collections.singletonMap(Bytes32.ZERO, Hash.EMPTY));
    final Block firstBlock = blockDataGenerator.block();
    final Block secondBlock =
        blockDataGenerator.block(
            BlockDataGenerator.BlockOptions.create().setParentHash(firstBlock.getHash()));
    privacyBlockProcessor.processBlock(blockchain, mutableWorldState, firstBlock);
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(firstBlock.getHash(), expected)
        .commit();
    privacyBlockProcessor.processBlock(blockchain, mutableWorldState, secondBlock);
    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(secondBlock.getHash()))
        .contains(expected);
    verify(blockProcessor)
        .processBlock(
            eq(blockchain),
            eq(mutableWorldState),
            eq(firstBlock.getHeader()),
            eq(firstBlock.getBody().getTransactions()),
            eq(firstBlock.getBody().getOmmers()),
            any());
    verify(blockProcessor)
        .processBlock(
            eq(blockchain),
            eq(mutableWorldState),
            eq(secondBlock.getHeader()),
            eq(secondBlock.getBody().getTransactions()),
            eq(secondBlock.getBody().getOmmers()),
            any());
  }

  @Test
  public void mustPerformRehydration() {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final Blockchain blockchain = mock(Blockchain.class);
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));

    final Block firstBlock =
        blockDataGenerator.block(
            BlockDataGenerator.BlockOptions.create()
                .addTransaction(PrivateTransactionDataFixture.privateMarkerTransactionOnchain()));
    final Block secondBlock =
        blockDataGenerator.block(
            BlockDataGenerator.BlockOptions.create()
                .addTransaction(
                    PrivateTransactionDataFixture.privateMarkerTransactionOnchainAdd()));

    when(enclave.receive(any()))
        .thenReturn(
            PrivateTransactionDataFixture.generateAddToGroupReceiveResponse(
                PrivateTransactionDataFixture.privateTransactionBesu(),
                PrivateTransactionDataFixture.privateMarkerTransactionOnchain()));
    when(blockchain.getTransactionLocation(any()))
        .thenReturn(Optional.of(new TransactionLocation(firstBlock.getHash(), 0)));
    when(blockchain.getBlockByHash(any())).thenReturn(Optional.of(firstBlock));
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(firstBlock.getHeader()));
    final ProtocolSpec protocolSpec = mockProtocolSpec();
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(publicWorldStateArchive.getMutable(any(), any()))
        .thenReturn(Optional.of(mutableWorldState));
    final MutableWorldState mockPrivateStateArchive = mockPrivateStateArchive();
    when(privateWorldStateArchive.getMutable(any(), any()))
        .thenReturn(Optional.of(mockPrivateStateArchive));

    final PrivacyGroupHeadBlockMap expected =
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(VALID_BASE64_ENCLAVE_KEY, firstBlock.getHash()));
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(firstBlock.getHash(), expected)
        .putPrivateBlockMetadata(
            firstBlock.getHash(), VALID_BASE64_ENCLAVE_KEY, PrivateBlockMetadata.empty())
        .commit();

    privacyBlockProcessor.processBlock(blockchain, mutableWorldState, secondBlock);
    verify(blockProcessor)
        .processBlock(
            eq(blockchain),
            eq(mutableWorldState),
            eq(secondBlock.getHeader()),
            eq(secondBlock.getBody().getTransactions()),
            eq(secondBlock.getBody().getOmmers()),
            any());
  }

  private MutableWorldState mockPrivateStateArchive() {
    final MutableWorldState mockPrivateState = mock(MutableWorldState.class);
    final WorldUpdater mockWorldUpdater = mock(WorldUpdater.class);
    final WrappedEvmAccount mockWrappedEvmAccount = mock(WrappedEvmAccount.class);
    final MutableAccount mockMutableAccount = mock(MutableAccount.class);
    when(mockWrappedEvmAccount.getMutable()).thenReturn(mockMutableAccount);
    when(mockWorldUpdater.createAccount(any())).thenReturn(mockWrappedEvmAccount);
    when(mockPrivateState.updater()).thenReturn(mockWorldUpdater);
    when(mockPrivateState.rootHash()).thenReturn(Hash.ZERO);
    return mockPrivateState;
  }

  private ProtocolSpec mockProtocolSpec() {
    final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    final MainnetTransactionProcessor mockPublicTransactionProcessor =
        mock(MainnetTransactionProcessor.class);
    when(mockPublicTransactionProcessor.processTransaction(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                Collections.emptyList(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));
    when(protocolSpec.getTransactionProcessor()).thenReturn(mockPublicTransactionProcessor);
    final PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    when(mockPrivateTransactionProcessor.processTransaction(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                Collections.emptyList(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));
    when(protocolSpec.getPrivateTransactionProcessor()).thenReturn(mockPrivateTransactionProcessor);
    final AbstractBlockProcessor.TransactionReceiptFactory mockTransactionReceiptFactory =
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class);
    when(mockTransactionReceiptFactory.create(any(), any(), any(), anyLong()))
        .thenReturn(new TransactionReceipt(0, 0, Collections.emptyList(), Optional.empty()));
    when(protocolSpec.getTransactionReceiptFactory()).thenReturn(mockTransactionReceiptFactory);
    when(protocolSpec.getBlockReward()).thenReturn(Wei.ZERO);
    when(protocolSpec.getMiningBeneficiaryCalculator())
        .thenReturn(mock(MiningBeneficiaryCalculator.class));
    when(protocolSpec.isSkipZeroBlockRewards()).thenReturn(true);
    return protocolSpec;
  }
}
