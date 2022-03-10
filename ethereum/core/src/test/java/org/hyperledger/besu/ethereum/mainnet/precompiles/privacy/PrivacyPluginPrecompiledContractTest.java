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
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION;
import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;
import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.serialize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.PrivacyGenesis;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupAuthProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PrivacyPluginPrecompiledContractTest {
  private final String DEFAULT_OUTPUT = "0x01";

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

    when(messageFrame.getContextVariable(KEY_IS_PERSISTING_PRIVATE_STATE, false)).thenReturn(false);
    when(messageFrame.hasContextVariable(KEY_PRIVATE_METADATA_UPDATER)).thenReturn(true);
    when(messageFrame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER))
        .thenReturn(mock(PrivateMetadataUpdater.class));
    when(messageFrame.getBlockValues()).thenReturn(block.getHeader());
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.empty()));

    final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
    when(messageFrame.hasContextVariable(KEY_PRIVATE_METADATA_UPDATER)).thenReturn(true);
    when(messageFrame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER))
        .thenReturn(privateMetadataUpdater);
    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap())
        .thenReturn(PrivacyGroupHeadBlockMap.empty());

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

                      @Override
                      public void setPrivacyGroupGenesisProvider(
                          final PrivacyGroupGenesisProvider privacyGroupAuthProvider) {}

                      @Override
                      public PrivacyGroupGenesisProvider getPrivacyGroupGenesisProvider() {
                        return (privacyGroupId, blockNumber) ->
                            (PrivacyGenesis) Collections::emptyList;
                      }

                      @Override
                      public PrivateMarkerTransactionFactory getPrivateMarkerTransactionFactory() {
                        return null;
                      }

                      @Override
                      public void setPrivateMarkerTransactionFactory(
                          final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {}
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

    when(messageFrame.getContextVariable(KEY_TRANSACTION)).thenReturn(transaction);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(payload, messageFrame);
    final Bytes actual = result.getOutput();

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
