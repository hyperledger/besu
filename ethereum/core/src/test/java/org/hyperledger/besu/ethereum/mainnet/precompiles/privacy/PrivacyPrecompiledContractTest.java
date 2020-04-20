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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.SpuriousDragonGasCalculator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyPrecompiledContractTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final String actual = "Test String";
  private final Bytes key = Bytes.wrap(actual.getBytes(UTF_8));
  private MessageFrame messageFrame;
  private Blockchain blockchain;
  private final String DEFAULT_OUTPUT = "0x01";
  final String PAYLOAD_TEST_PRIVACY_GROUP_ID = "8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=";
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  final PrivateStateRootResolver privateStateRootResolver =
      new PrivateStateRootResolver(privateStateStorage);

  private PrivateTransactionProcessor mockPrivateTxProcessor() {
    final PrivateTransactionProcessor mockPrivateTransactionProcessor =
        mock(PrivateTransactionProcessor.class);
    final List<Log> logs = new ArrayList<>();
    final PrivateTransactionProcessor.Result result =
        PrivateTransactionProcessor.Result.successful(
            logs, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null);
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

  @Before
  public void setUp() {
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any())).thenReturn(Optional.of(mutableWorldState));

    final PrivateStateStorage.Updater storageUpdater = mock(PrivateStateStorage.Updater.class);
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.EMPTY));
    when(privateStateStorage.getPrivateBlockMetadata(any(), any())).thenReturn(Optional.empty());
    when(storageUpdater.putPrivateBlockMetadata(
            nullable(Bytes32.class), nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putPrivacyGroupHeadBlockMap(nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(storageUpdater.putTransactionReceipt(
            nullable(Bytes32.class), nullable(Bytes32.class), any()))
        .thenReturn(storageUpdater);
    when(privateStateStorage.updater()).thenReturn(storageUpdater);

    messageFrame = mock(MessageFrame.class);
    blockchain = mock(Blockchain.class);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));
    when(blockchain.getGenesisBlock()).thenReturn(genesis);
    when(blockchain.getBlockByHash(block.getHash())).thenReturn(Optional.of(block));
    when(blockchain.getBlockByHash(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(messageFrame.getBlockchain()).thenReturn(blockchain);
    when(messageFrame.getBlockHeader()).thenReturn(block.getHeader());
  }

  @Test
  public void testPayloadFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            privateStateStorage,
            privateStateRootResolver);
    contract.setPrivateTransactionProcessor(mockPrivateTxProcessor());

    BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    PrivateTransactionDataFixture.privateTransactionBesu().writeTo(bytesValueRLPOutput);

    final ReceiveResponse response =
        new ReceiveResponse(
            bytesValueRLPOutput.encoded().toBase64String().getBytes(UTF_8),
            PAYLOAD_TEST_PRIVACY_GROUP_ID,
            null);
    when(enclave.receive(any(String.class))).thenReturn(response);

    final Bytes actual = contract.compute(key, messageFrame);

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void testPayloadNotFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);

    final PrivacyPrecompiledContract contract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            privateStateStorage,
            privateStateRootResolver);

    when(enclave.receive(any(String.class))).thenThrow(EnclaveClientException.class);

    final Bytes expected = contract.compute(key, messageFrame);
    assertThat(expected).isEqualTo(Bytes.EMPTY);
  }

  @Test(expected = RuntimeException.class)
  public void testEnclaveDown() {
    final Enclave enclave = mock(Enclave.class);

    final PrivacyPrecompiledContract contract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            privateStateStorage,
            privateStateRootResolver);

    when(enclave.receive(any(String.class))).thenThrow(new RuntimeException());

    contract.compute(key, messageFrame);
  }
}
