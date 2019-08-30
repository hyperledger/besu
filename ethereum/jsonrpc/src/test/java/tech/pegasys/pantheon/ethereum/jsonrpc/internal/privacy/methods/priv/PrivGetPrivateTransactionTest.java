/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.priv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.privacy.PrivateTransactionResult;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.Restriction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Optional;

import com.google.common.collect.Lists;
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

  private final PrivateTransaction.Builder privateTransactionBuilder =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
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
          .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .restriction(Restriction.RESTRICTED);

  private final String enclaveKey =
      BytesValues.fromBase64("93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=").toString();

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final Enclave enclave = mock(Enclave.class);

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);

  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);

  private final TransactionWithMetadata returnedTransaction = mock(TransactionWithMetadata.class);

  private final Transaction justTransaction = mock(Transaction.class);

  @Test
  public void returnsPrivateTransactionLegacy() throws Exception {
    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);
    when(justTransaction.getPayload()).thenReturn(BytesValues.fromBase64(""));

    final PrivateTransaction privateTransaction =
        privateTransactionBuilder
            .privateFor(
                Lists.newArrayList(
                    BytesValues.fromBase64("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
            .signAndBuild(KEY_PAIR);
    final PrivateTransactionLegacyResult privateTransactionLegacyResult =
        new PrivateTransactionLegacyResult(privateTransaction);

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, enclave, parameters, privacyParameters);
    final Object[] params = new Object[] {enclaveKey};
    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_getPrivateTransaction", params);

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    when(enclave.receive(any(ReceiveRequest.class)))
        .thenReturn(
            new ReceiveResponse(
                Base64.getEncoder().encodeToString(bvrlp.encoded().extractArray()).getBytes(UTF_8),
                ""));
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionResult result = (PrivateTransactionResult) response.getResult();

    assertThat(result).isEqualToComparingFieldByField(privateTransactionLegacyResult);
  }

  @Test
  public void returnsPrivateTransactionGroup() throws Exception {
    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);
    when(justTransaction.getPayload()).thenReturn(BytesValues.fromBase64(""));

    final PrivateTransaction privateTransaction =
        privateTransactionBuilder
            .privacyGroupId(BytesValues.fromBase64("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="))
            .signAndBuild(KEY_PAIR);
    final PrivateTransactionGroupResult privateTransactionGroupResult =
        new PrivateTransactionGroupResult(privateTransaction);

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, enclave, parameters, privacyParameters);

    final Object[] params = new Object[] {enclaveKey};
    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_getPrivateTransaction", params);

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    when(enclave.receive(any(ReceiveRequest.class)))
        .thenReturn(
            new ReceiveResponse(
                Base64.getEncoder().encodeToString(bvrlp.encoded().extractArray()).getBytes(UTF_8),
                ""));
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionResult result = (PrivateTransactionResult) response.getResult();

    assertThat(result).isEqualToComparingFieldByField(privateTransactionGroupResult);
  }
}
