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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.encodePrivateTransaction;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.generateAddToGroupReceiveResponse;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionBesu;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionLegacy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PrivateTransactionLocatorTest {

  private final String participantKey = "R24z0/bq4uTz0x1JxjdyRwfydh8Gi0L4oYYR0XpKdmc=";

  @Mock private Blockchain blockchain;
  @Mock private Enclave enclave;
  @Mock private PrivateStateStorage privateStateStorage;

  private PrivateTransactionLocator locator;

  @BeforeEach
  public void before() {
    locator = new PrivateTransactionLocator(blockchain, enclave, privateStateStorage);
  }

  @Test
  public void whenPmtDoesNotExistReturnEmpty() {
    final Hash pmtHash = Hash.hash(Bytes.random(32));
    when(blockchain.getTransactionLocation(eq(pmtHash))).thenReturn(Optional.empty());

    final Optional<ExecutedPrivateTransaction> tx = locator.findByPmtHash(pmtHash, participantKey);

    assertThat(tx).isEmpty();
  }

  @Test
  public void locateLegacyPrivateTransactionSentToOffchainPrivacyGroup() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransaction();
    final PrivateTransaction privateTransaction = privateTransactionLegacy();

    final ExecutedPrivateTransaction expectedPrivateTx =
        createExecutedPrivateTransaction(pmt, privateTransaction);

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isPresent().hasValue(expectedPrivateTx);
  }

  @Test
  public void locateBesuPrivateTransactionSentToOffchainPrivacyGroup() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransaction();
    final PrivateTransaction privateTransaction = privateTransactionBesu();

    final ExecutedPrivateTransaction expectedPrivateTx =
        createExecutedPrivateTransaction(pmt, privateTransaction);

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isPresent().hasValue(expectedPrivateTx);
  }

  private ExecutedPrivateTransaction createExecutedPrivateTransaction(
      final Transaction pmt, final PrivateTransaction privateTransaction) {
    final BlockHeader blockHeader = createBlockHeader();
    final TransactionLocation pmtLocation = createPmtLocation(pmt, blockHeader);

    // legacy transactions don't have a privacy group id, but Orion will return the internal privacy
    // group id.
    final String privacyGroupId =
        privateTransaction.getPrivacyGroupId().orElse(Bytes.random(32)).toBase64String();

    mockEnclaveForExistingPayload(privacyGroupId, pmt, privateTransaction, Optional.empty());

    return new ExecutedPrivateTransaction(
        blockHeader.getHash(),
        blockHeader.getNumber(),
        pmt.getHash(),
        pmtLocation.getTransactionIndex(),
        privacyGroupId,
        privateTransaction);
  }

  @Test
  public void locateBesuPrivateTransactionSentToFlexiblePrivacyGroup() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransactionOnchain();
    final PrivateTransaction privateTransaction = privateTransactionBesu();

    final ExecutedPrivateTransaction expectedPrivateTx =
        createExecutedPrivateTransactionWithVersion(pmt, privateTransaction);

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isPresent().hasValue(expectedPrivateTx);
  }

  private ExecutedPrivateTransaction createExecutedPrivateTransactionWithVersion(
      final Transaction pmt, final PrivateTransaction privateTransaction) {
    final BlockHeader blockHeader = createBlockHeader();
    final TransactionLocation pmtLocation = createPmtLocation(pmt, blockHeader);

    final String privacyGroupId =
        privateTransaction.getPrivacyGroupId().orElseThrow().toBase64String();

    final Bytes32 txVersion = Bytes32.wrap(Bytes.random(32));
    mockEnclaveForExistingPayload(privacyGroupId, pmt, privateTransaction, Optional.of(txVersion));

    return new ExecutedPrivateTransaction(
        blockHeader.getHash(),
        blockHeader.getNumber(),
        pmt.getHash(),
        pmtLocation.getTransactionIndex(),
        privacyGroupId,
        privateTransaction);
  }

  @Test
  public void locateBesuPrivateTransactionFromAddBlobSentToFlexiblePrivacyGroup() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransactionOnchain();
    final PrivateTransaction privateTransaction = privateTransactionBesu();

    final ExecutedPrivateTransaction expectedPrivateTx =
        createExecutedPrivateTransactionFromAddBlob(pmt, privateTransaction);

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isPresent().hasValue(expectedPrivateTx);
  }

  @Test
  public void locatePrivateTransactionWithNoEntryOnPGHeadBlockMap() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransactionOnchain();
    final PrivateTransaction privateTransaction = privateTransactionBesu();

    createExecutedPrivateTransactionFromAddBlob(pmt, privateTransaction);
    mockEnclaveForNonExistingPayload(pmt);
    // override private state removing any pg head block mapping
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any())).thenReturn(Optional.empty());

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isEmpty();
  }

  @Test
  public void locateBesuPrivateTransactionNotFoundInAddBlob() {
    final Transaction pmt = PrivateTransactionDataFixture.privateMarkerTransactionOnchain();
    final PrivateTransaction privateTransaction = privateTransactionBesu();

    createExecutedPrivateTransactionFromAddBlob(pmt, privateTransaction);
    mockEnlaveNoSingleTxOrAddBlob();

    final Optional<ExecutedPrivateTransaction> executedPrivateTx =
        locator.findByPmtHash(pmt.getHash(), participantKey);

    assertThat(executedPrivateTx).isEmpty();
  }

  /*
   Override enclave so it returns 404 when searching for a single tx (first call) and when
   searching for the add blob (second call)
  */
  private void mockEnlaveNoSingleTxOrAddBlob() {
    final EnclaveClientException payloadNotFoundException =
        new EnclaveClientException(404, "Payload not found");
    when(enclave.receive(anyString(), eq(participantKey)))
        .thenThrow(payloadNotFoundException)
        .thenThrow(payloadNotFoundException);
  }

  private ExecutedPrivateTransaction createExecutedPrivateTransactionFromAddBlob(
      final Transaction pmt, final PrivateTransaction privateTransaction) {
    final BlockHeader blockHeader = createBlockHeader();
    final TransactionLocation pmtLocation = createPmtLocation(pmt, blockHeader);
    final String privacyGroupId =
        privateTransaction.getPrivacyGroupId().orElseThrow().toBase64String();

    mockStorageWithPrivacyGroupBlockHeaderMap(privacyGroupId, blockHeader);

    final Bytes32 addDataKey = Bytes32.random();
    when(privateStateStorage.getAddDataKey(any(Bytes32.class))).thenReturn(Optional.of(addDataKey));

    mockEnclaveForNonExistingPayload(pmt);
    mockEnclaveForAddBlob(pmt, privateTransaction, addDataKey);

    return new ExecutedPrivateTransaction(
        blockHeader.getHash(),
        blockHeader.getNumber(),
        pmt.getHash(),
        pmtLocation.getTransactionIndex(),
        privacyGroupId,
        privateTransaction);
  }

  private void mockEnclaveForExistingPayload(
      final String privacyGroupId,
      final Transaction pmt,
      final PrivateTransaction privateTransaction,
      final Optional<Bytes32> version) {
    final String privateTransactionLookupId = pmt.getData().orElseThrow().toBase64String();
    final byte[] encodePrivateTransaction =
        encodePrivateTransaction(privateTransaction, version)
            .toBase64String()
            .getBytes(StandardCharsets.UTF_8);
    when(enclave.receive(eq(privateTransactionLookupId), eq(participantKey)))
        .thenReturn(new ReceiveResponse(encodePrivateTransaction, privacyGroupId, participantKey));
  }

  private void mockStorageWithPrivacyGroupBlockHeaderMap(
      final String privacyGroupId, final BlockHeader blockHeader) {
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(
                Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), blockHeader.getHash()));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(eq(blockHeader.getHash())))
        .thenReturn(Optional.of(privacyGroupHeadBlockMap));
  }

  private void mockEnclaveForNonExistingPayload(final Transaction pmt) {
    final String privateTransactionLookupId = pmt.getData().orElseThrow().toBase64String();
    final EnclaveClientException payloadNotFoundException =
        new EnclaveClientException(404, "Payload not found");
    when(enclave.receive(eq(privateTransactionLookupId), eq(participantKey)))
        .thenThrow(payloadNotFoundException);
  }

  private void mockEnclaveForAddBlob(
      final Transaction pmt,
      final PrivateTransaction privateTransaction,
      final Bytes32 addDataKey) {
    final ReceiveResponse addBlobResponse =
        generateAddToGroupReceiveResponse(privateTransaction, pmt);

    when(enclave.receive(eq(addDataKey.toBase64String()), eq(participantKey)))
        .thenReturn(addBlobResponse);
  }

  private BlockHeader createBlockHeader() {
    final Hash blockHash = Hash.hash(Bytes.random(32));
    final BlockHeader blockHeader = mock(BlockHeader.class);

    when(blockHeader.getHash()).thenReturn(blockHash);
    when(blockHeader.getNumber()).thenReturn(1L);
    when(blockchain.getBlockHeader(eq(blockHash))).thenReturn(Optional.of(blockHeader));

    return blockHeader;
  }

  private TransactionLocation createPmtLocation(
      final Transaction pmt, final BlockHeader blockHeader) {
    final TransactionLocation pmtLocation = new TransactionLocation(blockHeader.getHash(), 0);
    when(blockchain.getTransactionLocation(eq(pmt.getHash()))).thenReturn(Optional.of(pmtLocation));
    when(blockchain.getTransactionByHash(eq(pmt.getHash()))).thenReturn(Optional.of(pmt));
    return pmtLocation;
  }
}
