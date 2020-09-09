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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.versionedPrivateTransactionBesu;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveConfigurationException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.SpuriousDragonGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class OnChainPrivacyPrecompiledContractTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final Bytes txEnclaveKey = Bytes.random(32);
  private MessageFrame messageFrame;
  private Blockchain blockchain;
  private final String DEFAULT_OUTPUT = "0x01";
  final String PAYLOAD_TEST_PRIVACY_GROUP_ID = "8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=";
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  final PrivateStateRootResolver privateStateRootResolver =
      new PrivateStateRootResolver(privateStateStorage);

  private PrivateTransactionProcessor mockPrivateTxProcessor(
      final PrivateTransactionProcessor.Result result) {
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

  @Before
  public void setUp() {
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any())).thenReturn(Optional.of(mutableWorldState));

    final PrivateStateStorage.Updater storageUpdater = mock(PrivateStateStorage.Updater.class);
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(PrivacyGroupHeadBlockMap.empty()));
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
    final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap = mock(PrivacyGroupHeadBlockMap.class);
    when(messageFrame.getPrivateMetadataUpdater()).thenReturn(privateMetadataUpdater);
    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap()).thenReturn(privacyGroupHeadBlockMap);
  }

  @Test
  public void testPayloadFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);
    final OnChainPrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final List<Log> logs = new ArrayList<>();
    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            PrivateTransactionProcessor.Result.successful(
                logs, 0, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null)));

    final VersionedPrivateTransaction versionedPrivateTransaction =
        versionedPrivateTransactionBesu();
    final byte[] payload = convertVersionedPrivateTransactionToBytes(versionedPrivateTransaction);
    final String privateFrom =
        versionedPrivateTransaction.getPrivateTransaction().getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);
    when(enclave.receive(any())).thenReturn(response);

    final OnChainPrivacyPrecompiledContract contractSpy = spy(contract);
    Mockito.doNothing().when(contractSpy).maybeInjectDefaultManagementAndProxy(any(), any(), any());
    Mockito.doReturn(true)
        .when(contractSpy)
        .canExecute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    final Bytes actual = contractSpy.compute(txEnclaveKey, messageFrame);

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void testPayloadNotFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);

    when(enclave.receive(any(String.class))).thenThrow(EnclaveClientException.class);

    final Bytes expected = contract.compute(txEnclaveKey, messageFrame);
    assertThat(expected).isEqualTo(Bytes.EMPTY);
  }

  @Test(expected = RuntimeException.class)
  public void testEnclaveDown() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);

    when(enclave.receive(any(String.class))).thenThrow(new RuntimeException());

    contract.compute(txEnclaveKey, messageFrame);
  }

  @Test
  public void testEnclaveBelowRequiredVersion() {
    final Enclave enclave = mock(Enclave.class);
    final OnChainPrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final VersionedPrivateTransaction versionedPrivateTransaction =
        versionedPrivateTransactionBesu();
    final byte[] payload = convertVersionedPrivateTransactionToBytes(versionedPrivateTransaction);

    final ReceiveResponse responseWithoutSenderKey =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, null);
    when(enclave.receive(eq(txEnclaveKey.toBase64String()))).thenReturn(responseWithoutSenderKey);

    assertThatThrownBy(() -> contract.compute(txEnclaveKey, messageFrame))
        .isInstanceOf(EnclaveConfigurationException.class)
        .hasMessage("Incompatible Orion version. Orion version must be 1.6.0 or greater.");
  }

  @Test
  public void testPayloadNotMatchingPrivateFrom() {
    final Enclave enclave = mock(Enclave.class);
    final OnChainPrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final VersionedPrivateTransaction versionedPrivateTransaction =
        versionedPrivateTransactionBesu();
    final byte[] payload = convertVersionedPrivateTransactionToBytes(versionedPrivateTransaction);

    final String wrongSenderKey = Bytes.random(32).toBase64String();
    final ReceiveResponse responseWithWrongSenderKey =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, wrongSenderKey);
    when(enclave.receive(eq(txEnclaveKey.toBase64String()))).thenReturn(responseWithWrongSenderKey);

    final Bytes expected = contract.compute(txEnclaveKey, messageFrame);
    assertThat(expected).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testPrivateFromNotMemberOfGroup() {
    final Enclave enclave = mock(Enclave.class);
    final OnChainPrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final OnChainPrivacyPrecompiledContract contractSpy = spy(contract);
    Mockito.doNothing().when(contractSpy).maybeInjectDefaultManagementAndProxy(any(), any(), any());
    Mockito.doReturn(false)
        .when(contractSpy)
        .isContractLocked(any(), any(), any(), any(), any(), any(), any());
    Mockito.doReturn(true)
        .when(contractSpy)
        .onChainPrivacyGroupVersionMatches(any(), any(), any(), any(), any(), any(), any(), any());
    final PrivateTransactionProcessor.Result mockResult =
        mock(PrivateTransactionProcessor.Result.class);
    Mockito.doReturn(mockResult)
        .when(contractSpy)
        .simulateTransaction(any(), any(), any(), any(), any(), any(), any(), any());
    Mockito.doReturn(Arrays.asList(Bytes.ofUnsignedInt(1L)))
        .when(contractSpy)
        .getMembersFromResult(any());

    final VersionedPrivateTransaction privateTransaction = versionedPrivateTransactionBesu();
    final byte[] payload = convertVersionedPrivateTransactionToBytes(privateTransaction);
    final String privateFrom =
        privateTransaction.getPrivateTransaction().getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);
    when(enclave.receive(any(String.class))).thenReturn(response);

    final Bytes actual = contractSpy.compute(txEnclaveKey, messageFrame);

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testInvalidPrivateTransaction() {
    final Enclave enclave = mock(Enclave.class);

    final OnChainPrivacyPrecompiledContract contract =
        new OnChainPrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            privateStateRootResolver);

    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            PrivateTransactionProcessor.Result.invalid(
                ValidationResult.invalid(
                    TransactionValidator.TransactionInvalidReason.INCORRECT_NONCE))));

    final OnChainPrivacyPrecompiledContract contractSpy = spy(contract);
    Mockito.doNothing().when(contractSpy).maybeInjectDefaultManagementAndProxy(any(), any(), any());
    Mockito.doReturn(true)
        .when(contractSpy)
        .canExecute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    final VersionedPrivateTransaction privateTransaction = versionedPrivateTransactionBesu();
    final byte[] payload = convertVersionedPrivateTransactionToBytes(privateTransaction);
    final String privateFrom =
        privateTransaction.getPrivateTransaction().getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);

    when(enclave.receive(any(String.class))).thenReturn(response);

    final Bytes actual = contractSpy.compute(txEnclaveKey, messageFrame);

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  private byte[] convertVersionedPrivateTransactionToBytes(
      final VersionedPrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(bytesValueRLPOutput);

    return bytesValueRLPOutput.encoded().toBase64String().getBytes(UTF_8);
  }

  private OnChainPrivacyPrecompiledContract buildPrivacyPrecompiledContract(final Enclave enclave) {
    return new OnChainPrivacyPrecompiledContract(
        new SpuriousDragonGasCalculator(), enclave, worldStateArchive, privateStateRootResolver);
  }
}
