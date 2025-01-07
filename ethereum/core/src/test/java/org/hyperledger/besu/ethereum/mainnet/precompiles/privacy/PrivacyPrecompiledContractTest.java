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
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_BASE64_ENCLAVE_KEY;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionBesu;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveConfigurationException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("MockNotUsedInProduction")
public class PrivacyPrecompiledContractTest {

  private final String actual = "Test String";
  private final Bytes privateTransactionLookupId = Bytes.wrap(actual.getBytes(UTF_8));
  private MessageFrame messageFrame;
  private final Blockchain blockchain = mock(Blockchain.class);
  private final String DEFAULT_OUTPUT = "0x01";
  final String PAYLOAD_TEST_PRIVACY_GROUP_ID = "8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=";
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
  final PrivateStateRootResolver privateStateRootResolver =
      new PrivateStateRootResolver(privateStateStorage);

  final PrivateStateGenesisAllocator privateStateGenesisAllocator =
      new PrivateStateGenesisAllocator(
          false, (privacyGroupId, blockNumber) -> Collections::emptyList);

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

  @BeforeEach
  public void setUp() {
    final MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(mutableWorldState.updater()).thenReturn(mock(WorldUpdater.class));
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(worldStateArchive.getMutable(any(), any())).thenReturn(Optional.of(mutableWorldState));

    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap())
        .thenReturn(PrivacyGroupHeadBlockMap.empty());
    when(privateMetadataUpdater.getPrivateBlockMetadata(any()))
        .thenReturn(new PrivateBlockMetadata(Collections.emptyList()));

    messageFrame = mock(MessageFrame.class);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock();
    final Block block =
        blockGenerator.block(
            new BlockDataGenerator.BlockOptions().setParentHash(genesis.getHeader().getHash()));
    when(blockchain.getGenesisBlock()).thenReturn(genesis);
    when(blockchain.getBlockByHash(block.getHash())).thenReturn(Optional.of(block));
    when(blockchain.getBlockByHash(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(messageFrame.getBlockValues()).thenReturn(block.getHeader());
    final PrivateMetadataUpdater privateMetadataUpdater = mock(PrivateMetadataUpdater.class);
    when(messageFrame.getContextVariable(KEY_IS_PERSISTING_PRIVATE_STATE, false)).thenReturn(false);
    when(messageFrame.hasContextVariable(KEY_PRIVATE_METADATA_UPDATER)).thenReturn(true);
    when(messageFrame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER))
        .thenReturn(privateMetadataUpdater);
    when(privateMetadataUpdater.getPrivacyGroupHeadBlockMap())
        .thenReturn(PrivacyGroupHeadBlockMap.empty());
  }

  @Test
  public void testPayloadFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);
    when(enclave.retrievePrivacyGroup(PAYLOAD_TEST_PRIVACY_GROUP_ID))
        .thenReturn(
            new PrivacyGroup(
                PAYLOAD_TEST_PRIVACY_GROUP_ID,
                PrivacyGroup.Type.PANTHEON,
                "",
                "",
                Arrays.asList(VALID_BASE64_ENCLAVE_KEY.toBase64String())));
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final List<Log> logs = new ArrayList<>();
    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            TransactionProcessingResult.successful(
                logs, 0, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null)));

    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);
    final String privateFrom = privateTransaction.getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);
    when(enclave.receive(any(String.class))).thenReturn(response);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(privateTransactionLookupId, messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void testPayloadNotFoundInEnclave() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);

    when(enclave.receive(any(String.class))).thenThrow(EnclaveClientException.class);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(privateTransactionLookupId, messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testEnclaveDown() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);

    when(enclave.receive(any(String.class))).thenThrow(new RuntimeException());

    assertThatThrownBy(() -> contract.computePrecompile(privateTransactionLookupId, messageFrame))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void testEnclaveBelowRequiredVersion() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);

    final ReceiveResponse responseWithoutSenderKey =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, null);
    when(enclave.receive(eq(privateTransactionLookupId.toBase64String())))
        .thenReturn(responseWithoutSenderKey);

    assertThatThrownBy(() -> contract.computePrecompile(privateTransactionLookupId, messageFrame))
        .isInstanceOf(EnclaveConfigurationException.class)
        .hasMessage("Incompatible Orion version. Orion version must be 1.6.0 or greater.");
  }

  @Test
  public void testPrivateTransactionWithoutPrivateFrom() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final PrivateTransaction privateTransaction = spy(privateTransactionBesu());
    when(privateTransaction.getPrivateFrom()).thenReturn(Bytes.EMPTY);
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);

    final String senderKey = privateTransaction.getPrivateFrom().toBase64String();
    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, senderKey);
    when(enclave.receive(eq(privateTransactionLookupId.toBase64String()))).thenReturn(response);

    final Bytes expected = contract.compute(privateTransactionLookupId, messageFrame);
    assertThat(expected).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testPayloadNotMatchingPrivateFrom() {
    final Enclave enclave = mock(Enclave.class);
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);

    final String wrongSenderKey = Bytes.random(32).toBase64String();
    final ReceiveResponse responseWithWrongSenderKey =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, wrongSenderKey);
    when(enclave.receive(eq(privateTransactionLookupId.toBase64String())))
        .thenReturn(responseWithWrongSenderKey);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(privateTransactionLookupId, messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testPrivateFromNotMemberOfGroup() {
    final Enclave enclave = mock(Enclave.class);
    when(enclave.retrievePrivacyGroup(PAYLOAD_TEST_PRIVACY_GROUP_ID))
        .thenReturn(
            new PrivacyGroup(
                PAYLOAD_TEST_PRIVACY_GROUP_ID,
                PrivacyGroup.Type.PANTHEON,
                "",
                "",
                Arrays.asList(VALID_BASE64_ENCLAVE_KEY.toBase64String())));
    final PrivacyPrecompiledContract contract = buildPrivacyPrecompiledContract(enclave);
    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            TransactionProcessingResult.successful(
                new ArrayList<>(), 0, 0, Bytes.fromHexString(DEFAULT_OUTPUT), null)));

    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);
    final String privateFrom = privateTransaction.getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);
    when(enclave.receive(any(String.class))).thenReturn(response);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(privateTransactionLookupId, messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.fromHexString(DEFAULT_OUTPUT));
  }

  @Test
  public void testInvalidPrivateTransaction() {
    final Enclave enclave = mock(Enclave.class);
    when(enclave.retrievePrivacyGroup(PAYLOAD_TEST_PRIVACY_GROUP_ID))
        .thenReturn(
            new PrivacyGroup(
                PAYLOAD_TEST_PRIVACY_GROUP_ID,
                PrivacyGroup.Type.PANTHEON,
                "",
                "",
                Arrays.asList(VALID_BASE64_ENCLAVE_KEY.toBase64String())));
    final PrivacyPrecompiledContract contract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(),
            enclave,
            worldStateArchive,
            privateStateRootResolver,
            privateStateGenesisAllocator,
            false,
            "RestrictedPrivacyTest");

    contract.setPrivateTransactionProcessor(
        mockPrivateTxProcessor(
            TransactionProcessingResult.invalid(
                ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH))));

    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final byte[] payload = convertPrivateTransactionToBytes(privateTransaction);
    final String privateFrom = privateTransaction.getPrivateFrom().toBase64String();

    final ReceiveResponse response =
        new ReceiveResponse(payload, PAYLOAD_TEST_PRIVACY_GROUP_ID, privateFrom);

    when(enclave.receive(any(String.class))).thenReturn(response);

    final PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(privateTransactionLookupId, messageFrame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  @Test
  public void testSimulatedPublicTransactionIsSkipped() {
    final PrivacyPrecompiledContract emptyContract =
        new PrivacyPrecompiledContract(null, null, null, null, null, false, null);

    // A simulated public transaction doesn't contain a PrivateMetadataUpdater
    final MessageFrame frame = mock(MessageFrame.class);
    when(frame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER)).thenReturn(null);

    final PrecompiledContract.PrecompileContractResult result =
        emptyContract.computePrecompile(null, frame);
    final Bytes actual = result.getOutput();

    assertThat(actual).isEqualTo(Bytes.EMPTY);
  }

  private byte[] convertPrivateTransactionToBytes(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(bytesValueRLPOutput);

    return bytesValueRLPOutput.encoded().toBase64String().getBytes(UTF_8);
  }

  private PrivacyPrecompiledContract buildPrivacyPrecompiledContract(final Enclave enclave) {
    return new PrivacyPrecompiledContract(
        new SpuriousDragonGasCalculator(),
        enclave,
        worldStateArchive,
        privateStateRootResolver,
        privateStateGenesisAllocator,
        false,
        "PrivacyTests");
  }
}
