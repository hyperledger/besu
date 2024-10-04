/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobAndProofV1;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import io.vertx.core.Vertx;
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
public class EngineGetBlobsV1Test {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final SECPPrivateKey PRIVATE_KEY1 =
      SIGNATURE_ALGORITHM
          .get()
          .createPrivateKey(
              Bytes32.fromHexString(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"));
  private static final KeyPair KEYS1 =
      new KeyPair(PRIVATE_KEY1, SIGNATURE_ALGORITHM.get().createPublicKey(PRIVATE_KEY1));
  public static final VersionedHash VERSIONED_HASH_ZERO = new VersionedHash((byte) 1, Hash.ZERO);

  @Mock private ProtocolContext protocolContext;
  @Mock private EngineCallListener engineCallListener;
  @Mock private MutableBlockchain blockchain;
  @Mock private TransactionPool transactionPool;

  private EngineGetBlobsV1 method;

  private static final Vertx vertx = Vertx.vertx();

  @BeforeEach
  public void before() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method =
        spy(new EngineGetBlobsV1(vertx, protocolContext, engineCallListener, transactionPool));
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getBlobsV1");
  }

  @Test
  public void shouldReturnBlobsAndProofsForKnownVersionedHashesFromMap() {
    final Transaction blobTransaction = createBlobTransaction();

    final BlobsWithCommitments blobsWithCommitments =
        blobTransaction.getBlobsWithCommitments().get();

    mockTransactionPoolMethod(blobsWithCommitments);

    VersionedHash[] versionedHashes =
        blobsWithCommitments.getVersionedHashes().toArray(new VersionedHash[0]);

    final JsonRpcResponse jsonRpcResponse = resp(versionedHashes);

    final List<BlobAndProofV1> blobAndProofV1s = fromSuccessResp(jsonRpcResponse);

    assertThat(blobAndProofV1s.size()).isEqualTo(versionedHashes.length);
    // for loop to check each blob and proof
    for (int i = 0; i < versionedHashes.length; i++) {
      assertThat(Bytes.fromHexString(blobAndProofV1s.get(i).getBlob()))
          .isEqualTo(blobsWithCommitments.getBlobQuads().get(i).blob().getData());
      assertThat(Bytes.fromHexString(blobAndProofV1s.get(i).getProof()))
          .isEqualTo(blobsWithCommitments.getBlobQuads().get(i).kzgProof().getData());
    }
  }

  @Test
  public void shouldReturnNullForBlobsAndProofsForUnknownVersionedHashes() {
    final Transaction blobTransaction = createBlobTransaction();

    final BlobsWithCommitments blobsWithCommitments =
        blobTransaction.getBlobsWithCommitments().get();

    mockTransactionPoolMethod(blobsWithCommitments);

    List<VersionedHash> versionedHashesList =
        blobsWithCommitments.getVersionedHashes().stream().toList();

    final VersionedHash[] hashes = versionedHashesList.toArray(new VersionedHash[0]);
    hashes[1] = VERSIONED_HASH_ZERO;

    final JsonRpcResponse jsonRpcResponse = resp(hashes);

    final List<BlobAndProofV1> blobAndProofV1s = fromSuccessResp(jsonRpcResponse);

    assertThat(blobAndProofV1s.size()).isEqualTo(versionedHashesList.size());
    // for loop to check each blob and proof
    for (int i = 0; i < versionedHashesList.size(); i++) {
      if (i != 1) {
        assertThat(Bytes.fromHexString(blobAndProofV1s.get(i).getBlob()))
            .isEqualTo(blobsWithCommitments.getBlobQuads().get(i).blob().getData());
        assertThat(Bytes.fromHexString(blobAndProofV1s.get(i).getProof()))
            .isEqualTo(blobsWithCommitments.getBlobQuads().get(i).kzgProof().getData());
      } else {
        assertThat(blobAndProofV1s.get(i)).isNull();
      }
    }
  }

  @Test
  public void shouldReturnOnlyNullsForBlobsAndProofsIfAllVersionedHashesUnknown() {
    final Transaction blobTransaction = createBlobTransaction();

    final BlobsWithCommitments blobsWithCommitments =
        blobTransaction.getBlobsWithCommitments().get();

    mockTransactionPoolMethod(blobsWithCommitments);

    List<VersionedHash> versionedHashesList =
        blobsWithCommitments.getVersionedHashes().stream().toList();

    final VersionedHash[] versionedHashes = new VersionedHash[6];
    Arrays.fill(versionedHashes, VERSIONED_HASH_ZERO);
    final JsonRpcResponse jsonRpcResponse = resp(versionedHashes);

    final List<BlobAndProofV1> blobAndProofV1s = fromSuccessResp(jsonRpcResponse);

    assertThat(blobAndProofV1s.size()).isEqualTo(versionedHashesList.size());
    // for loop to check each blob and proof
    for (int i = 0; i < versionedHashes.length; i++) {
      assertThat(blobAndProofV1s.get(i)).isNull();
    }
  }

  @Test
  public void shouldReturnEmptyResponseForEmptyRequest() {
    final VersionedHash[] versionedHashes = new VersionedHash[0];

    final JsonRpcResponse jsonRpcResponse = resp(versionedHashes);

    assertThat(fromSuccessResp(jsonRpcResponse).size()).isEqualTo(0);
  }

  @Test
  public void shouldFailWhenRequestingMoreThan128() {
    final VersionedHash[] versionedHashes = new VersionedHash[129];
    for (int i = 0; i < 129; i++) {
      versionedHashes[i] = new VersionedHash((byte) 1, Hash.ZERO);
    }

    final JsonRpcResponse jsonRpcResponse = resp(versionedHashes);

    assertThat(fromErrorResp(jsonRpcResponse).getCode())
        .isEqualTo(RpcErrorType.INVALID_ENGINE_GET_BLOBS_V1_TOO_LARGE_REQUEST.getCode());
    assertThat(fromErrorResp(jsonRpcResponse).getMessage())
        .isEqualTo(RpcErrorType.INVALID_ENGINE_GET_BLOBS_V1_TOO_LARGE_REQUEST.getMessage());
  }

  Transaction createBlobTransaction() {
    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(6);
    TransactionTestFixture ttf = new TransactionTestFixture();
    Transaction fullOfBlobs =
        ttf.to(Optional.of(Address.ZERO))
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.valueOf(42)))
            .gasLimit(21000)
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerBlobGas(Optional.of(Wei.of(128)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .versionedHashes(Optional.of(bwc.getVersionedHashes()))
            .blobsWithCommitments(Optional.of(bwc))
            .createTransaction(KEYS1);
    return fullOfBlobs;
  }

  private void mockTransactionPoolMethod(final BlobsWithCommitments blobsWithCommitments) {
    blobsWithCommitments
        .getBlobQuads()
        .forEach(
            blobQuad ->
                when(transactionPool.getBlobQuad(blobQuad.versionedHash())).thenReturn(blobQuad));
  }

  private JsonRpcResponse resp(final VersionedHash[] versionedHashes) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_GET_BLOBS_V1.getMethodName(),
                new Object[] {versionedHashes})));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<BlobAndProofV1> fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(RpcResponseType.SUCCESS);
    final List list =
        Optional.of(resp)
            .map(JsonRpcSuccessResponse.class::cast)
            .map(JsonRpcSuccessResponse::getResult)
            .map(List.class::cast)
            .get();
    final ArrayList<BlobAndProofV1> blobAndProofV1s = new ArrayList<>();
    list.forEach(obj -> blobAndProofV1s.add((BlobAndProofV1) obj));
    return blobAndProofV1s;
  }

  private RpcErrorType fromErrorResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(RpcResponseType.ERROR);
    return Optional.of(resp)
        .map(JsonRpcErrorResponse.class::cast)
        .map(JsonRpcErrorResponse::getErrorType)
        .get();
  }
}
