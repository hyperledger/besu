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
package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionBesu;
import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;
import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.serialize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.SpuriousDragonGasCalculator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupAuthProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PrivacyPluginPrecompiledContractTest {
  private final String DEFAULT_OUTPUT = "0x01";
  private final Blockchain blockchain = mock(Blockchain.class);

  MessageFrame messageFrame;

  PrivacyPluginPrecompiledContract contract;

  @Before
  public void setup() {
    final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);

    messageFrame = mock(MessageFrame.class);

    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));

    when(messageFrame.getPrivateMetadataUpdater()).thenReturn(mock(PrivateMetadataUpdater.class));
    when(messageFrame.getBlockHeader()).thenReturn(block.getHeader());
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.empty()));

    final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
    when(messageFrame.getPrivateMetadataUpdater()).thenReturn(privateMetadataUpdater);
    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap())
        .thenReturn(PrivacyGroupHeadBlockMap.empty());

    when(messageFrame.getBlockchain()).thenReturn(blockchain);

    contract =
        new PrivacyPluginPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            new PrivacyParameters.Builder()
                .setEnabled(true)
                .setPrivacyPluginEnabled(true)
                .setStorageProvider(new InMemoryPrivacyStorageProvider())
                .setPrivacyService(
                    new PrivacyPluginService() {
                      @Override
                      public void setPayloadProvider(final PrivacyPluginPayloadProvider provider) {}

                      @Override
                      public PrivacyPluginPayloadProvider getPayloadProvider() {
                        return new PrivacyPluginPayloadProvider() {
                          @Override
                          public Bytes generateMarkerPayload(
                              final org.hyperledger.besu.plugin.data.PrivateTransaction
                                  privateTransaction,
                              final String privacyUserId) {
                            return serialize(privateTransaction).encoded();
                          }

                          @Override
                          public Optional<org.hyperledger.besu.plugin.data.PrivateTransaction>
                              getPrivateTransactionFromPayload(
                                  final org.hyperledger.besu.plugin.data.Transaction transaction) {
                            final BytesValueRLPInput bytesValueRLPInput =
                                new BytesValueRLPInput(transaction.getPayload(), false);
                            return Optional.of(readFrom(bytesValueRLPInput));
                          }
                        };
                      }

                      @Override
                      public void setPrivacyGroupAuthProvider(
                          final PrivacyGroupAuthProvider privacyGroupAuthProvider) {}

                      @Override
                      public PrivacyGroupAuthProvider getPrivacyGroupAuthProvider() {
                        return (privacyGroupId, privacyUserId, blockNumber) -> true;
                      }
                    })
                .setEnclaveFactory(mock(EnclaveFactory.class))
                .build());
  }

  @Test
  public void testPayloadFoundInPayloadOfMarker() {
    final List<Log> logs = new ArrayList<>();
    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            TransactionProcessingResult.successful(
                logs, 0, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null)));

    final PrivateTransaction privateTransaction = privateTransactionBesu();

    final Bytes payload = convertPrivateTransactionToBytes(privateTransaction);

    final Transaction transaction = Transaction.builder().payload(payload).build();

    when(messageFrame.getTransaction()).thenReturn(transaction);

    final Bytes actual = contract.compute(payload, messageFrame);

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  private Bytes convertPrivateTransactionToBytes(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(bytesValueRLPOutput);

    return bytesValueRLPOutput.encoded();
  }

  private PrivateTransactionProcessor mockPrivateTxProcessor(
      final TransactionProcessingResult result) {
    final PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    when(mockPrivateTransactionProcessor.processTransaction(
            nullable(Blockchain.class),
            nullable(WorldUpdater.class),
            nullable(WorldUpdater.class),
            nullable(ProcessableBlockHeader.class),
            nullable((Hash.class)),
            nullable(PrivateTransaction.class),
            nullable(Address.class),
            nullable(OperationTracer.class),
            nullable(BlockHashLookup.class),
            nullable(Bytes.class)))
        .thenReturn(result);

    return mockPrivateTransactionProcessor;
  }
}
