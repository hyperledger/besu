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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivGetPrivateTransactionTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String TRANSACTION_HASH =
      Bytes.fromBase64String("5bpr9tz4zhmWmk9RlNng93Ky7lXwFkMc7+ckoFgUMku=").toString();
  private static final Bytes ENCLAVE_KEY =
      Bytes.fromBase64String("93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=");

  private final PrivateTransaction.Builder privateTransactionBuilder =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              Bytes.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f60003960"
                      + "00f3fe60806040526004361060485763ffffffff7c01000000"
                      + "00000000000000000000000000000000000000000000000000"
                      + "60003504166360fe47b18114604d5780636d4ce63c14607557"
                      + "5b600080fd5b348015605857600080fd5b5060736004803603"
                      + "6020811015606d57600080fd5b50356099565b005b34801560"
                      + "8057600080fd5b506087609e565b6040805191825251908190"
                      + "0360200190f35b600055565b6000549056fea165627a7a7230"
                      + "5820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6"
                      + "daa4f6b2f003d1b0180029"))
          .sender(sender)
          .chainId(BigInteger.valueOf(2018))
          .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .restriction(Restriction.RESTRICTED);

  private final Enclave enclave = mock(Enclave.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final TransactionWithMetadata returnedTransaction = mock(TransactionWithMetadata.class);
  private final Transaction justTransaction = mock(Transaction.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);
  private final User user =
      new JWTUser(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), "");
  private final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Before
  public void before() {
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    when(privacyParameters.isEnabled()).thenReturn(true);
  }

  @Test
  public void returnsPrivateTransactionLegacy() {
    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);
    when(justTransaction.getPayload()).thenReturn(ENCLAVE_KEY);

    final PrivateTransaction privateTransaction =
        privateTransactionBuilder
            .privateFor(
                Lists.newArrayList(
                    Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
            .signAndBuild(KEY_PAIR);
    final PrivateTransactionLegacyResult privateTransactionLegacyResult =
        new PrivateTransactionLegacyResult(privateTransaction);

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, privacyController, enclavePublicKeyProvider);
    final Object[] params = new Object[] {TRANSACTION_HASH};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getPrivateTransaction", params), user);

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenReturn(
            new ReceiveResponse(
                Base64.getEncoder().encodeToString(bvrlp.encoded().toArray()).getBytes(UTF_8),
                "",
                null));
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionResult result = (PrivateTransactionResult) response.getResult();

    assertThat(result).isEqualToComparingFieldByField(privateTransactionLegacyResult);
    verify(privacyController).retrieveTransaction(ENCLAVE_KEY.toBase64String(), ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void returnsPrivateTransactionGroup() {
    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);
    when(justTransaction.getPayload()).thenReturn(Bytes.fromBase64String(""));

    final PrivateTransaction privateTransaction =
        privateTransactionBuilder
            .privacyGroupId(Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="))
            .signAndBuild(KEY_PAIR);
    final PrivateTransactionGroupResult privateTransactionGroupResult =
        new PrivateTransactionGroupResult(privateTransaction);

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, privacyController, enclavePublicKeyProvider);

    final Object[] params = new Object[] {TRANSACTION_HASH};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_getPrivateTransaction", params));

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenReturn(
            new ReceiveResponse(
                Base64.getEncoder().encodeToString(bvrlp.encoded().toArrayUnsafe()).getBytes(UTF_8),
                "",
                null));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionResult result = (PrivateTransactionResult) response.getResult();

    assertThat(result).isEqualToComparingFieldByField(privateTransactionGroupResult);
  }

  @Test
  public void failsWithEnclaveErrorOnEnclaveError() {
    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);
    when(justTransaction.getPayload()).thenReturn(Bytes.fromBase64String(""));

    final PrivateTransaction privateTransaction =
        privateTransactionBuilder
            .privacyGroupId(Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="))
            .signAndBuild(KEY_PAIR);

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, privacyController, enclavePublicKeyProvider);

    final Object[] params = new Object[] {TRANSACTION_HASH};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_getPrivateTransaction", params));

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenThrow(new EnclaveClientException(500, "enclave failure"));

    final JsonRpcResponse response = privGetPrivateTransaction.response(request);
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);
    assertThat(response).isEqualTo(expectedResponse);
  }
}
