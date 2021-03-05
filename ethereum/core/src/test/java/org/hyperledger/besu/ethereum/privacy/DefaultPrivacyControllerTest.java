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

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.orion.testutil.OrionKeyUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DefaultPrivacyControllerTest {

  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
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
  private static final byte[] PAYLOAD = new byte[0];
  private static final List<String> PRIVACY_GROUP_ADDRESSES = newArrayList("8f2a", "fb23");
  private static final String PRIVACY_GROUP_NAME = "pg_name";
  private static final String PRIVACY_GROUP_DESCRIPTION = "pg_desc";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String ENCLAVE_KEY2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String PRIVACY_GROUP_ID = "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=";
  private static final ArrayList<Log> LOGS = new ArrayList<>();
  private static final String MOCK_TRANSACTION_SIMULATOR_RESULT_OUTPUT_BYTES_PREFIX =
      "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002";
  private static final PrivacyGroup PANTHEON_PRIVACY_GROUP =
      new PrivacyGroup("", PrivacyGroup.Type.PANTHEON, "", "", emptyList());
  private static final PrivacyGroup ONCHAIN_PRIVACY_GROUP =
      new PrivacyGroup("", PrivacyGroup.Type.ONCHAIN, "", "", Arrays.asList(ENCLAVE_PUBLIC_KEY));

  private PrivacyController privacyController;
  private PrivacyController brokenPrivacyController;
  private PrivateTransactionValidator privateTransactionValidator;
  private Enclave enclave;
  private String enclavePublicKey;
  private PrivateNonceProvider privateNonceProvider;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateTransactionSimulator privateTransactionSimulator;
  private PrivateWorldStateReader privateWorldStateReader;
  private Blockchain blockchain;
  private PrivateStateStorage privateStateStorage;

  private static final Transaction PUBLIC_TRANSACTION =
      Transaction.builder()
          .type(TransactionType.FRONTIER)
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
          .value(Wei.ZERO)
          .payload(Base64.decode(TRANSACTION_KEY))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(BigInteger.valueOf(2018))
          .signAndBuild(KEY_PAIR);

  private Enclave mockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    final SendResponse response = new SendResponse(TRANSACTION_KEY);
    final ReceiveResponse receiveResponse =
        new ReceiveResponse(new byte[0], PRIVACY_GROUP_ID, null);
    when(mockEnclave.send(anyString(), anyString(), anyList())).thenReturn(response);
    when(mockEnclave.send(anyString(), anyString(), anyString())).thenReturn(response);
    when(mockEnclave.receive(any(), any())).thenReturn(receiveResponse);
    return mockEnclave;
  }

  private Enclave brokenMockEnclave() {
    final Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.send(anyString(), anyString(), anyList()))
        .thenThrow(EnclaveServerException.class);
    return mockEnclave;
  }

  private PrivateTransactionValidator mockPrivateTransactionValidator() {
    final PrivateTransactionValidator validator = mock(PrivateTransactionValidator.class);
    when(validator.validate(any(), any(), anyBoolean())).thenReturn(ValidationResult.valid());
    return validator;
  }

  @Before
  public void setUp() throws Exception {
    blockchain = mock(Blockchain.class);
    privateTransactionSimulator = mock(PrivateTransactionSimulator.class);
    privateStateStorage = mock(PrivateStateStorage.class);
    privateNonceProvider = mock(ChainHeadPrivateNonceProvider.class);
    privateStateRootResolver = mock(PrivateStateRootResolver.class);
    when(privateNonceProvider.getNonce(any(), any())).thenReturn(1L);

    privateWorldStateReader = mock(PrivateWorldStateReader.class);

    enclavePublicKey = OrionKeyUtils.loadKey("orion_key_0.pub");
    privateTransactionValidator = mockPrivateTransactionValidator();
    enclave = mockEnclave();

    privacyController =
        new DefaultPrivacyController(
            blockchain,
            privateStateStorage,
            enclave,
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR),
            privateTransactionSimulator,
            privateNonceProvider,
            privateWorldStateReader,
            privateStateRootResolver);
    brokenPrivacyController =
        new DefaultPrivacyController(
            blockchain,
            privateStateStorage,
            brokenMockEnclave(),
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR),
            privateTransactionSimulator,
            privateNonceProvider,
            privateWorldStateReader,
            privateStateRootResolver);
  }

  @Test
  public void sendsValidLegacyTransaction() {
    final PrivateTransaction transaction = buildLegacyPrivateTransaction(1);

    final String privateTransactionLookupId =
        privacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY, Optional.empty());

    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ENCLAVE_PUBLIC_KEY);

    final Transaction markerTransaction =
        privacyController.createPrivacyMarkerTransaction(privateTransactionLookupId, transaction);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
    assertThat(markerTransaction.contractAddress()).isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(markerTransaction.getPayload()).isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(markerTransaction.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(markerTransaction.getSender()).isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(markerTransaction.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
    verify(enclave)
        .send(anyString(), eq(ENCLAVE_PUBLIC_KEY), eq(List.of(ENCLAVE_PUBLIC_KEY, ENCLAVE_KEY2)));
  }

  @Test
  public void sendValidBesuTransaction() {
    final PrivateTransaction transaction = buildBesuPrivateTransaction(1);

    final String privateTransactionLookupId =
        privacyController.sendTransaction(
            transaction, ENCLAVE_PUBLIC_KEY, Optional.of(PANTHEON_PRIVACY_GROUP));

    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ENCLAVE_PUBLIC_KEY);

    final Transaction markerTransaction =
        privacyController.createPrivacyMarkerTransaction(privateTransactionLookupId, transaction);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
    assertThat(markerTransaction.contractAddress()).isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(markerTransaction.getPayload()).isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(markerTransaction.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(markerTransaction.getSender()).isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(markerTransaction.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
    verify(enclave).send(anyString(), eq(ENCLAVE_PUBLIC_KEY), eq(PRIVACY_GROUP_ID));
  }

  @Test
  public void findOnChainPrivacyGroups() {
    final List<String> privacyGroupAddresses = newArrayList(ENCLAVE_PUBLIC_KEY, ENCLAVE_KEY2);

    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID, PrivacyGroup.Type.ONCHAIN, "", "", privacyGroupAddresses);

    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            Map.of(Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID)), Hash.ZERO));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any()))
        .thenReturn(Optional.of(privacyGroupHeadBlockMap));

    when(privateTransactionSimulator.process(any(), any()))
        .thenReturn(
            Optional.of(
                new TransactionProcessingResult(
                    TransactionProcessingResult.Status.SUCCESSFUL,
                    emptyList(),
                    0,
                    0,
                    Bytes.fromHexString(
                        MOCK_TRANSACTION_SIMULATOR_RESULT_OUTPUT_BYTES_PREFIX
                            + Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY).toUnprefixedHexString()
                            + Bytes.fromBase64String(ENCLAVE_KEY2).toUnprefixedHexString()),
                    ValidationResult.valid(),
                    Optional.empty())));

    final List<PrivacyGroup> privacyGroups =
        privacyController.findOnChainPrivacyGroupByMembers(
            privacyGroupAddresses, ENCLAVE_PUBLIC_KEY);
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups.get(0)).isEqualToComparingFieldByField(privacyGroup);
    verify(privateStateStorage).getPrivacyGroupHeadBlockMap(any());
    verify(privateTransactionSimulator).process(any(), any());
  }

  @Test
  public void sendTransactionWhenEnclaveFailsThrowsEnclaveError() {
    assertThatExceptionOfType(EnclaveServerException.class)
        .isThrownBy(
            () ->
                brokenPrivacyController.sendTransaction(
                    buildLegacyPrivateTransaction(), ENCLAVE_PUBLIC_KEY, Optional.empty()));
  }

  @Test
  public void validateTransactionWithTooLowNonceReturnsError() {
    when(privateTransactionValidator.validate(any(), any(), anyBoolean()))
        .thenReturn(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(0);
    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ENCLAVE_PUBLIC_KEY);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void validateTransactionWithIncorrectNonceReturnsError() {
    when(privateTransactionValidator.validate(any(), any(), anyBoolean()))
        .thenReturn(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(2);

    final ValidationResult<TransactionInvalidReason> validationResult =
        privacyController.validatePrivateTransaction(transaction, ENCLAVE_PUBLIC_KEY);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  @Test
  public void retrievesTransaction() {
    when(enclave.receive(anyString(), anyString()))
        .thenReturn(new ReceiveResponse(PAYLOAD, PRIVACY_GROUP_ID, null));

    final ReceiveResponse receiveResponse =
        privacyController.retrieveTransaction(TRANSACTION_KEY, ENCLAVE_PUBLIC_KEY);

    assertThat(receiveResponse.getPayload()).isEqualTo(PAYLOAD);
    assertThat(receiveResponse.getPrivacyGroupId()).isEqualTo(PRIVACY_GROUP_ID);
    verify(enclave).receive(TRANSACTION_KEY, enclavePublicKey);
  }

  @Test
  public void createsPrivacyGroup() {
    final PrivacyGroup enclavePrivacyGroupResponse =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            PrivacyGroup.Type.PANTHEON,
            PRIVACY_GROUP_NAME,
            PRIVACY_GROUP_DESCRIPTION,
            PRIVACY_GROUP_ADDRESSES);
    when(enclave.createPrivacyGroup(any(), any(), any(), any()))
        .thenReturn(enclavePrivacyGroupResponse);

    final PrivacyGroup privacyGroup =
        privacyController.createPrivacyGroup(
            PRIVACY_GROUP_ADDRESSES,
            PRIVACY_GROUP_NAME,
            PRIVACY_GROUP_DESCRIPTION,
            ENCLAVE_PUBLIC_KEY);

    assertThat(privacyGroup).isEqualToComparingFieldByField(enclavePrivacyGroupResponse);
    verify(enclave)
        .createPrivacyGroup(
            PRIVACY_GROUP_ADDRESSES,
            enclavePublicKey,
            PRIVACY_GROUP_NAME,
            PRIVACY_GROUP_DESCRIPTION);
  }

  @Test
  public void deletesPrivacyGroup() {
    when(enclave.deletePrivacyGroup(anyString(), anyString())).thenReturn(PRIVACY_GROUP_ID);

    final String deletedPrivacyGroupId =
        privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY);

    assertThat(deletedPrivacyGroupId).isEqualTo(PRIVACY_GROUP_ID);
    verify(enclave).deletePrivacyGroup(PRIVACY_GROUP_ID, enclavePublicKey);
  }

  @Test
  public void findsPrivacyGroup() {
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            PrivacyGroup.Type.PANTHEON,
            PRIVACY_GROUP_NAME,
            PRIVACY_GROUP_DESCRIPTION,
            PRIVACY_GROUP_ADDRESSES);
    when(enclave.findPrivacyGroup(any())).thenReturn(new PrivacyGroup[] {privacyGroup});

    final PrivacyGroup[] privacyGroups =
        privacyController.findOffChainPrivacyGroupByMembers(
            PRIVACY_GROUP_ADDRESSES, ENCLAVE_PUBLIC_KEY);
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups[0]).isEqualToComparingFieldByField(privacyGroup);
    verify(enclave).findPrivacyGroup(PRIVACY_GROUP_ADDRESSES);
  }

  @Test
  public void determinesNonceForEeaRequest() {
    final Address address = Address.fromHexString("55");
    final long reportedNonce = 8L;
    final PrivacyGroup[] returnedGroups =
        new PrivacyGroup[] {
          new PrivacyGroup(
              PRIVACY_GROUP_ID,
              PrivacyGroup.Type.LEGACY,
              "Group1_Name",
              "Group1_Desc",
              emptyList()),
        };

    when(enclave.findPrivacyGroup(any())).thenReturn(returnedGroups);
    when(privateNonceProvider.getNonce(any(Address.class), any(Bytes32.class))).thenReturn(8L);

    final long nonce =
        privacyController.determineEeaNonce(
            ENCLAVE_PUBLIC_KEY, new String[] {ENCLAVE_KEY2}, address, ENCLAVE_PUBLIC_KEY);

    assertThat(nonce).isEqualTo(reportedNonce);
    verify(enclave)
        .findPrivacyGroup(
            argThat((m) -> m.containsAll(newArrayList(ENCLAVE_PUBLIC_KEY, ENCLAVE_KEY2))));
  }

  @Test
  public void determineNonceForEeaRequestWithNoMatchingGroupReturnsZero() {
    final long reportedNonce = 0L;
    final Address address = Address.fromHexString("55");
    final PrivacyGroup[] returnedGroups = new PrivacyGroup[0];

    when(enclave.findPrivacyGroup(any())).thenReturn(returnedGroups);

    final long nonce =
        privacyController.determineEeaNonce(
            "privateFrom", new String[] {"first", "second"}, address, ENCLAVE_PUBLIC_KEY);

    assertThat(nonce).isEqualTo(reportedNonce);
    verify(enclave)
        .findPrivacyGroup(
            argThat((m) -> m.containsAll(newArrayList("first", "second", "privateFrom"))));
  }

  @Test
  public void determineNonceForEeaRequestWithMoreThanOneMatchingGroupThrowsException() {
    final Address address = Address.fromHexString("55");
    final PrivacyGroup[] returnedGroups =
        new PrivacyGroup[] {
          new PrivacyGroup(
              "Group1", PrivacyGroup.Type.LEGACY, "Group1_Name", "Group1_Desc", emptyList()),
          new PrivacyGroup(
              "Group2", PrivacyGroup.Type.LEGACY, "Group2_Name", "Group2_Desc", emptyList()),
        };

    when(enclave.findPrivacyGroup(any())).thenReturn(returnedGroups);

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                privacyController.determineEeaNonce(
                    "privateFrom", new String[] {"first", "second"}, address, ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void simulatingPrivateTransactionWorks() {
    final CallParameter callParameter = mock(CallParameter.class);
    when(privateTransactionSimulator.process(any(), any(), any(long.class)))
        .thenReturn(
            Optional.of(
                TransactionProcessingResult.successful(
                    LOGS, 0, 0, Bytes.EMPTY, ValidationResult.valid())));
    final Optional<TransactionProcessingResult> result =
        privacyController.simulatePrivateTransaction(
            "Group1", ENCLAVE_PUBLIC_KEY, callParameter, 1);
    assertThat(result.isPresent()).isTrue();
  }

  @Test
  public void canCreatePrivacyMarkerTransactionForOnChainPrivacy() {
    final PrivateTransaction transaction = buildBesuPrivateTransaction(0);

    final String privateTransactionLookupId =
        privacyController.sendTransaction(
            transaction, ENCLAVE_PUBLIC_KEY, Optional.of(ONCHAIN_PRIVACY_GROUP));

    final Transaction onChainPrivacyMarkerTransaction =
        privacyController.createPrivacyMarkerTransaction(
            privateTransactionLookupId, transaction, Address.ONCHAIN_PRIVACY);

    assertThat(onChainPrivacyMarkerTransaction.contractAddress())
        .isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(onChainPrivacyMarkerTransaction.getPayload())
        .isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(onChainPrivacyMarkerTransaction.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(onChainPrivacyMarkerTransaction.getSender())
        .isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(onChainPrivacyMarkerTransaction.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
    assertThat(onChainPrivacyMarkerTransaction.getTo().get()).isEqualTo(Address.ONCHAIN_PRIVACY);
    verify(enclave)
        .send(anyString(), eq(ENCLAVE_PUBLIC_KEY), eq(singletonList(ENCLAVE_PUBLIC_KEY)));
  }

  @Test
  public void getContractCodeCallsPrivateWorldStateReader() {
    final Hash blockHash = Hash.ZERO;
    final Address contractAddress = Address.ZERO;
    final Bytes contractCode = Bytes.fromBase64String("ZXhhbXBsZQ==");

    when(privateWorldStateReader.getContractCode(
            eq(PRIVACY_GROUP_ID), eq(blockHash), eq(contractAddress)))
        .thenReturn(Optional.of(contractCode));

    assertThat(
            privacyController.getContractCode(
                PRIVACY_GROUP_ID, contractAddress, blockHash, ENCLAVE_PUBLIC_KEY))
        .isPresent()
        .hasValue(contractCode);
  }

  private static PrivateTransaction buildLegacyPrivateTransaction() {
    return buildLegacyPrivateTransaction(0);
  }

  private static PrivateTransaction buildLegacyPrivateTransaction(final long nonce) {
    return buildPrivateTransaction(nonce)
        .privateFrom(Base64.decode(ENCLAVE_PUBLIC_KEY))
        .privateFor(newArrayList(Base64.decode(ENCLAVE_PUBLIC_KEY), Base64.decode(ENCLAVE_KEY2)))
        .signAndBuild(KEY_PAIR);
  }

  private static PrivateTransaction buildBesuPrivateTransaction(final long nonce) {

    return buildPrivateTransaction(nonce)
        .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY))
        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
        .signAndBuild(KEY_PAIR);
  }

  private static PrivateTransaction.Builder buildPrivateTransaction(final long nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
        .value(Wei.ZERO)
        .payload(Bytes.fromHexString("0x"))
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(2018))
        .restriction(Restriction.RESTRICTED);
  }
}
