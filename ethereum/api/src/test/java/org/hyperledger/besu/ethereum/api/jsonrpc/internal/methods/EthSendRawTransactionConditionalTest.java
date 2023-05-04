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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthSendRawTransactionConditionalTest {

  private static final String VALID_TRANSACTION =
      "0xf86d0485174876e800830222e0945aae326516b4f8fe08074b7e972e40a713048d62880de0b6b3a7640000801ba05d4e7998757264daab67df2ce6f7e7a0ae36910778a406ca73898c9899a32b9ea0674700d5c3d1d27f2e6b4469957dfd1a1c49bf92383d80717afc84eb05695d5b";
  private final TransactionPool transactionPool = mock(TransactionPool.class);
  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private EthSendRawTransactionConditional method;

  @BeforeEach
  public void before() {
    method = new EthSendRawTransactionConditional(blockchainQueries, transactionPool);
  }

  @Test
  public void maxBlockNumber_lessThanHead_returnsError() throws JsonProcessingException {
    when(blockchainQueries.headBlockNumber()).thenReturn(99L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransactionConditional\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions, "block number not within specified range");
  }

  @Test
  public void minBlockNumber_greaterThanHead_returnsError() throws JsonProcessingException {
    when(blockchainQueries.headBlockNumber()).thenReturn(89L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransactionConditional\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions, "block number not within specified range");
  }

  @Test
  public void maxTimestamp_lessThanHead_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7557L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransactionConditional\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions, "timestamp not within specified range");
  }

  @Test
  public void minTimestamp_greaterThanHead_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7337L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransactionConditional\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions, "timestamp not within specified range");
  }

  @Test
  public void validTransactionIsSentToTransactionPool() {
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eth_sendRawTransactionConditional", new String[] {VALID_TRANSACTION}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0xbaabcc1bd699e7378451e4ce5969edb9bdcae76cb79bdacae793525c31e423c7");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eth_sendRawTransactionConditional");
  }

  //  private SendRawTransactionConditionalParameter parameterWithNoConditions() {
  //    return new SendRawTransactionConditionalParameter(null, null, null, null, null);
  //  }
  //
  //  private SendRawTransactionConditionalParameter parameterWithBlockNumberConditions(
  //          final long blockNumberMin, final long blockNumberMax) {
  //    return new SendRawTransactionConditionalParameter(
  //            blockNumberMin, blockNumberMax, null, null, null);
  //  }
  //
  //  private SendRawTransactionConditionalParameter parameterWithTimestampConditions(
  //          final long timestampMin, final long timestampMax) {
  //    return new SendRawTransactionConditionalParameter(null, null, null, timestampMin,
  // timestampMax);
  //  }
  //
  //  private SendRawTransactionConditionalParameter parameterWithKnownAccountConditions(
  //          final Map<Address, Hash> knownAccounts) {
  //    return new SendRawTransactionConditionalParameter(null, null, knownAccounts, null, null);
  //  }

  private void assertActualResponseIsErrorWithGivenMessage(
      final String jsonRequestString, final String message) throws JsonProcessingException {

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new ObjectMapper().readValue(jsonRequestString, JsonRpcRequest.class));
    final JsonRpcResponse actualResponse = method.response(request);
    // the JsonRpcError.data field gets reset by the enum
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(actualResponse.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    // test for the exact error message in data field
    assertThat(((JsonRpcErrorResponse) actualResponse).getError().getData()).isEqualTo(message);
  }
}
