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
package org.hyperledger.besu.ethereum.privacy;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.enclave.testutil.EnclaveKeyUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Base64;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FlexiblePrivacyControllerTest {

  private static final String ADDRESS1 = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String ADDRESS2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String PRIVACY_GROUP_ID = "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=";
  private static final String KEY = "C2bVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String HEX_STRING_32BYTES_VALUE1 =
      "0x0000000000000000000000000000000000000000000000000000000000000001";

  private FlexiblePrivacyController privacyController;
  private PrivateTransactionValidator privateTransactionValidator;
  private PrivateNonceProvider privateNonceProvider;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateTransactionSimulator privateTransactionSimulator;
  private PrivateWorldStateReader privateWorldStateReader;
  private Blockchain blockchain;
  private PrivateStateStorage privateStateStorage;
  private Enclave enclave;
  private String enclavePublicKey;
  private FlexiblePrivacyController brokenPrivacyController;
  private static final byte[] PAYLOAD = new byte[0];
  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";

  private static final List<String> PRIVACY_GROUP_ADDRESSES = List.of(ADDRESS1, ADDRESS2);
  private static final Bytes SIMULATOR_RESULT_PREFIX =
      Bytes.fromHexString(
          "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002");
  private static final Bytes SIMULATOR_RESULT =
      Bytes.concatenate(SIMULATOR_RESULT_PREFIX, Base64.decode(ADDRESS1), Base64.decode(ADDRESS2));
  private static final PrivacyGroup EXPECTED_PRIVACY_GROUP =
      new PrivacyGroup(
          PRIVACY_GROUP_ID, PrivacyGroup.Type.FLEXIBLE, "", "", PRIVACY_GROUP_ADDRESSES);

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair KEY_PAIR =
      SIGNATURE_ALGORITHM
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  @Before
  public void setUp() throws Exception {
    blockchain = mock(Blockchain.class);
    privateTransactionSimulator = mock(PrivateTransactionSimulator.class);
    privateStateStorage = mock(PrivateStateStorage.class);
    privateNonceProvider = mock(ChainHeadPrivateNonceProvider.class);
    privateStateRootResolver = mock(PrivateStateRootResolver.class);

    privateWorldStateReader = mock(PrivateWorldStateReader.class);

    privateTransactionValidator = mockPrivateTransactionValidator();
    enclave = mockEnclave();

    enclavePublicKey = EnclaveKeyUtils.loadKey("enclave_key_0.pub");

    privacyController =
        new FlexiblePrivacyController(
            blockchain,
            privateStateStorage,
            enclave,
            privateTransactionValidator,
            privateTransactionSimulator,
            privateNonceProvider,
            privateWorldStateReader,
            privateStateRootResolver);
    brokenPrivacyController =
        new FlexiblePrivacyController(
            blockchain,
            privateStateStorage,
            brokenMockEnclave(),
            privateTransactionValidator,
            privateTransactionSimulator,
            privateNonceProvider,
            privateWorldStateReader,
            privateStateRootResolver);
  }

  @Test
  public void createsPayload() {
    final PrivateTransaction privateTransaction = buildPrivateTransaction(1).signAndBuild(KEY_PAIR);
    final SendResponse key = new SendResponse(KEY);
    when(enclave.send(any(), any(), anyList())).thenReturn(key);
    final String payload =
        privacyController.createPrivateMarkerTransactionPayload(
            privateTransaction, ADDRESS1, Optional.of(EXPECTED_PRIVACY_GROUP));
    assertThat(payload).isNotNull().isEqualTo(KEY);
  }

  @Test
  public void createsPayloadForAdding() {
    mockingForCreatesPayloadForAdding();
    final PrivateTransaction privateTransaction =
        buildPrivateTransaction(1)
            .payload(FlexibleGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE)
            .to(PrivacyParameters.FLEXIBLE_PRIVACY_PROXY)
            .signAndBuild(KEY_PAIR);
    final String payload =
        privacyController.createPrivateMarkerTransactionPayload(
            privateTransaction, ADDRESS1, Optional.of(EXPECTED_PRIVACY_GROUP));
    assertThat(payload)
        .isNotNull()
        .isEqualTo(
            Bytes.concatenate(Bytes.fromBase64String(KEY), Bytes.fromBase64String(KEY))
                .toBase64String());
  }

  @Test
  public void verifiesGroupContainsUserId() {
    final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract =
        mock(FlexiblePrivacyGroupContract.class);
    when(flexiblePrivacyGroupContract.getPrivacyGroupByIdAndBlockNumber(any(), any()))
        .thenReturn(Optional.of(EXPECTED_PRIVACY_GROUP));
    privacyController.setFlexiblePrivacyGroupContract(flexiblePrivacyGroupContract);
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ADDRESS1);
  }

  @Test
  public void findFleixblePrivacyGroups() {
    mockingForFindPrivacyGroupByMembers();
    mockingForFindPrivacyGroupById();

    final List<PrivacyGroup> privacyGroups =
        List.of(privacyController.findPrivacyGroupByMembers(PRIVACY_GROUP_ADDRESSES, ADDRESS1));
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups.get(0)).usingRecursiveComparison().isEqualTo(EXPECTED_PRIVACY_GROUP);
    verify(privateStateStorage).getPrivacyGroupHeadBlockMap(any());
    verify(privateTransactionSimulator).process(any(), any());
  }

  @Test
  public void createsPrivacyGroup() {
    Assertions.assertThatThrownBy(
            () -> privacyController.createPrivacyGroup(Collections.emptyList(), "", "", ADDRESS1))
        .isInstanceOf(PrivacyConfigurationNotSupportedException.class)
        .hasMessageContaining("Method not supported when using flexible privacy");
  }

  @Test
  public void deletesPrivacyGroup() {
    Assertions.assertThatThrownBy(
            () -> privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ADDRESS1))
        .isInstanceOf(PrivacyConfigurationNotSupportedException.class)
        .hasMessageContaining("Method not supported when using flexible privacy");
  }

  @Test
  public void sendTransactionWhenEnclaveFailsThrowsEnclaveError() {
    final TransactionProcessingResult transactionProcessingResult =
        new TransactionProcessingResult(
            TransactionProcessingResult.Status.SUCCESSFUL,
            Collections.emptyList(),
            0,
            0,
            Bytes32.ZERO,
            ValidationResult.valid(),
            Optional.empty());
    when(privateTransactionSimulator.process(any(), any()))
        .thenReturn(Optional.of(transactionProcessingResult));
    Assertions.setMaxStackTraceElementsDisplayed(500);
    assertThatExceptionOfType(EnclaveServerException.class)
        .isThrownBy(
            () ->
                brokenPrivacyController.createPrivateMarkerTransactionPayload(
                    buildPrivateTransaction(0).signAndBuild(KEY_PAIR),
                    ADDRESS1,
                    Optional.of(EXPECTED_PRIVACY_GROUP)));
  }

  @Test
  public void validateTransactionWithTooLowNonceReturnsError() {
    when(privateTransactionValidator.validate(any(), any(), anyBoolean()))
        .thenReturn(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));

    final PrivateTransaction transaction = buildPrivateTransaction(0).build();
    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ADDRESS1);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void validateTransactionWithIncorrectNonceReturnsError() {
    when(privateTransactionValidator.validate(any(), any(), anyBoolean()))
        .thenReturn(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));

    final PrivateTransaction transaction = buildPrivateTransaction(2).build();

    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ADDRESS1);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  @Test
  public void retrievesTransaction() {
    when(enclave.receive(anyString(), anyString()))
        .thenReturn(new ReceiveResponse(PAYLOAD, PRIVACY_GROUP_ID, null));

    final ReceiveResponse receiveResponse =
        privacyController.retrieveTransaction(TRANSACTION_KEY, ADDRESS1);

    assertThat(receiveResponse.getPayload()).isEqualTo(PAYLOAD);
    assertThat(receiveResponse.getPrivacyGroupId()).isEqualTo(PRIVACY_GROUP_ID);
    verify(enclave).receive(TRANSACTION_KEY, enclavePublicKey);
  }

  @Test
  public void findsPrivacyGroupById() {
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID, PrivacyGroup.Type.FLEXIBLE, "", "", PRIVACY_GROUP_ADDRESSES);
    mockingForFindPrivacyGroupById();

    final Optional<PrivacyGroup> privacyGroupFound =
        privacyController.findPrivacyGroupByGroupId(PRIVACY_GROUP_ID, ADDRESS1);
    assertThat(privacyGroupFound).isPresent();
    assertThat(privacyGroupFound.get()).usingRecursiveComparison().isEqualTo(privacyGroup);
  }

  @Test
  public void findsPrivacyGroupByIdEmpty() {
    when(privateTransactionSimulator.process(any(), any())).thenReturn(Optional.empty());

    final Optional<PrivacyGroup> privacyGroupFound =
        privacyController.findPrivacyGroupByGroupId(PRIVACY_GROUP_ID, ADDRESS1);
    assertThat(privacyGroupFound).isEmpty();
  }

  private Enclave brokenMockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.send(anyString(), anyString(), anyList()))
        .thenThrow(EnclaveServerException.class);
    return mockEnclave;
  }

  private static PrivateTransaction.Builder buildPrivateTransaction(final long nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
        .value(Wei.ZERO)
        .payload(Bytes.fromHexString("0x00"))
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(1337))
        .restriction(Restriction.RESTRICTED)
        .privateFrom(Base64.decode(ADDRESS1))
        .privacyGroupId(Base64.decode(PRIVACY_GROUP_ID));
  }

  private Enclave mockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    return mockEnclave;
  }

  private PrivateTransactionValidator mockPrivateTransactionValidator() {
    final PrivateTransactionValidator validator = mock(PrivateTransactionValidator.class);
    return validator;
  }

  private void mockingForFindPrivacyGroupByMembers() {
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            Map.of(Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID)), Hash.ZERO));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(privacyGroupHeadBlockMap));
  }

  private void mockingForFindPrivacyGroupById() {
    when(privateTransactionSimulator.process(any(), any()))
        .thenReturn(
            Optional.of(
                new TransactionProcessingResult(
                    TransactionProcessingResult.Status.SUCCESSFUL,
                    emptyList(),
                    0,
                    0,
                    SIMULATOR_RESULT,
                    ValidationResult.valid(),
                    Optional.empty())));
  }

  private void mockingForCreatesPayloadForAdding() {
    final SendResponse key = new SendResponse(KEY);
    when(enclave.send(any(), any(), anyList())).thenReturn(key);
    final Map<Bytes32, Hash> bytes32HashMap = new HashMap<>();
    final Bytes32 pgBytes = Bytes32.wrap(Base64.decode(PRIVACY_GROUP_ID));
    bytes32HashMap.put(pgBytes, Hash.ZERO);
    when(blockchain.getChainHeadHash()).thenReturn(Hash.ZERO);
    final Optional<PrivacyGroupHeadBlockMap> privacyGroupHeadBlockMap =
        Optional.of(new PrivacyGroupHeadBlockMap(bytes32HashMap));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(Hash.ZERO))
        .thenReturn(privacyGroupHeadBlockMap);
    final List<PrivateTransactionMetadata> privateTransactionMetadata =
        List.of(new PrivateTransactionMetadata(Hash.ZERO, Hash.ZERO));
    final PrivateBlockMetadata privateBlockMetadata =
        new PrivateBlockMetadata(privateTransactionMetadata);
    when(privateStateStorage.getPrivateBlockMetadata(any(), eq(pgBytes)))
        .thenReturn(Optional.of(privateBlockMetadata));
    final BlockHeader blockHeaderMock = mock(BlockHeader.class);
    final Optional<BlockHeader> maybeBlockHeader = Optional.of(blockHeaderMock);
    when(blockchain.getBlockHeader(any())).thenReturn(maybeBlockHeader);
    final Hash hash0x01 = Hash.fromHexString(HEX_STRING_32BYTES_VALUE1);
    when(blockHeaderMock.getParentHash()).thenReturn(hash0x01);
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(hash0x01)).thenReturn(Optional.empty());
    final Transaction transaction =
        Transaction.builder()
            .gasLimit(0)
            .gasPrice(Wei.ZERO)
            .nonce(0)
            .payload(Bytes.fromHexString(HEX_STRING_32BYTES_VALUE1))
            .value(Wei.ZERO)
            .to(null)
            .guessType()
            .signAndBuild(KEY_PAIR);
    when(blockchain.getTransactionByHash(any())).thenReturn(Optional.ofNullable(transaction));
    final PrivateTransactionWithMetadata privateTransactionWithMetadata =
        new PrivateTransactionWithMetadata(
            buildPrivateTransaction(3).signAndBuild(KEY_PAIR),
            new PrivateTransactionMetadata(Hash.ZERO, Hash.ZERO));
    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    privateTransactionWithMetadata.writeTo(bytesValueRLPOutput);
    final byte[] txPayload =
        bytesValueRLPOutput.encoded().toBase64String().getBytes(StandardCharsets.UTF_8);
    when(enclave.receive(any(), any()))
        .thenReturn(new ReceiveResponse(txPayload, PRIVACY_GROUP_ID, ADDRESS2));
  }
}
