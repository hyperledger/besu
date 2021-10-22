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
package org.hyperledger.besu.ethereum.goquorum;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoQuorumBlockProcessorTest {

  @Mock private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  @Mock private GoQuorumEnclave goQuorumEnclave;
  @Mock private GoQuorumPrivateStorage goQuorumPrivateStorage;
  @Mock private MainnetTransactionProcessor transactionProcessor;
  @Mock private MainnetTransactionValidator transactionValidator;

  private GoQuorumPrivacyParameters goQuorumPrivacyParameters;

  @Before
  public void setup() {
    goQuorumPrivacyParameters =
        new GoQuorumPrivacyParameters(goQuorumEnclave, "123", goQuorumPrivateStorage, null);
  }

  @Test
  public void noAccountCreatedWhenBlockRewardIsZeroAndSkipped() {
    final Blockchain blockchain = new ReferenceTestBlockchain();
    final GoQuorumBlockProcessor blockProcessor =
        new GoQuorumBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true,
            Optional.of(goQuorumPrivacyParameters));

    final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());
    final Hash initialHash = worldState.rootHash();

    final BlockHeader emptyBlockHeader =
        new BlockHeaderTestFixture()
            .transactionsRoot(Hash.EMPTY_LIST_HASH)
            .ommersHash(Hash.EMPTY_LIST_HASH)
            .buildHeader();
    blockProcessor.processBlock(blockchain, worldState, emptyBlockHeader, emptyList(), emptyList());

    // An empty block with 0 reward should not change the world state
    assertThat(worldState.rootHash()).isEqualTo(initialHash);
  }

  @Test
  public void accountCreatedWhenBlockRewardIsZeroAndNotSkipped() {
    final Blockchain blockchain = new ReferenceTestBlockchain();
    final GoQuorumBlockProcessor blockProcessor =
        new GoQuorumBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            false,
            Optional.of(goQuorumPrivacyParameters));

    final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());
    final Hash initialHash = worldState.rootHash();

    final BlockHeader emptyBlockHeader =
        new BlockHeaderTestFixture()
            .transactionsRoot(Hash.EMPTY_LIST_HASH)
            .ommersHash(Hash.EMPTY_LIST_HASH)
            .buildHeader();
    blockProcessor.processBlock(blockchain, worldState, emptyBlockHeader, emptyList(), emptyList());

    // An empty block with 0 reward should change the world state prior to EIP158
    assertThat(worldState.rootHash()).isNotEqualTo(initialHash);
  }

  @Test
  public void enclaveNotAvailable() {
    final Blockchain blockchain = new ReferenceTestBlockchain();
    final GoQuorumBlockProcessor blockProcessor =
        new GoQuorumBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            false,
            Optional.of(goQuorumPrivacyParameters));

    final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());

    final Block block = mock(Block.class);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    final BlockBody blockBody = mock(BlockBody.class);
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getGasLimit()).thenReturn(1000L);
    when(transaction.isGoQuorumPrivateTransaction(true)).thenReturn(true);
    when(transaction.getPayload()).thenReturn(Bytes.wrap(new byte[] {(byte) 1}));
    when(block.getBody()).thenReturn(blockBody);
    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockBody.getOmmers()).thenReturn(Collections.emptyList());
    when(blockHeader.getNumber()).thenReturn(20000L);
    when(blockHeader.getGasLimit()).thenReturn(20000L);
    when(block.getHeader()).thenReturn(blockHeader);
    when(goQuorumEnclave.receive(any())).thenThrow(new EnclaveServerException(1, "a"));
    when(transactionProcessor.getTransactionValidator()).thenReturn(transactionValidator);
    when(transactionValidator.getGoQuorumCompatibilityMode()).thenReturn(true);

    assertThatThrownBy(() -> blockProcessor.processBlock(blockchain, worldState, worldState, block))
        .isExactlyInstanceOf(EnclaveServerException.class)
        .hasMessageContaining("a");
  }
}
