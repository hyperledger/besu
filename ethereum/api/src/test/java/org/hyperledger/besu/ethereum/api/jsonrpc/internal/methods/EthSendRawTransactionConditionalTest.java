/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSendRawTransactionConditional.MAX_CONDITIONS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.EXCEEDS_RPC_MAX_CONDITIONS_SIZE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.USER_SPECIFIED_CONDITIONS_NOT_MET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
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

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthSendRawTransactionConditionalTest {

  private static final String VALID_TRANSACTION =
      "0xf86d0485174876e800830222e0945aae326516b4f8fe08074b7e972e40a713048d62880de0b6b3a7640000801ba05d4e7998757264daab67df2ce6f7e7a0ae36910778a406ca73898c9899a32b9ea0674700d5c3d1d27f2e6b4469957dfd1a1c49bf92383d80717afc84eb05695d5b";
  public static final String METHOD_NAME = "eth_XsendRawTransactionConditional";
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
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "block number 99 not within specified range min 90 max 98");
  }

  @Test
  public void minBlockNumber_greaterThanHead_returnsError() throws JsonProcessingException {
    when(blockchainQueries.headBlockNumber()).thenReturn(89L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "block number 89 not within specified range min 90 max 98");
  }

  @Test
  public void maxTimestamp_lessThanHead_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7557L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "timestamp 7557 not within specified range min 7339 max 7447");
  }

  @Test
  public void minTimestamp_greaterThanHead_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7337L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);

    final String jsonWithBlockConditions =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithBlockConditions,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "timestamp 7337 not within specified range min 7339 max 7447");
  }

  @Test
  public void knownAccount_storageEntry_hasChanged_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    // timestamp within the min/max range
    when(header.getTimestamp()).thenReturn(7437L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    // block number within the min/max range
    final long headBlockNumber = 93L;
    when(blockchainQueries.headBlockNumber()).thenReturn(headBlockNumber);
    // storage entries
    final Address address = Address.fromHexString("0x000000000000000000000000000000000099abcd");
    when(blockchainQueries.storageAt(address, UInt256.ONE, headBlockNumber))
        .thenReturn(Optional.of(UInt256.fromBytes(Bytes.fromHexString("0x54be"))));
    when(blockchainQueries.storageAt(address, UInt256.ZERO, headBlockNumber))
        .thenReturn(Optional.of(UInt256.fromBytes(Bytes.fromHexString("0xbe00"))));

    final String jsonWithKnownAccounts =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\","
            + "\"knownAccounts\": "
            + "{\"0x000000000000000000000000000000000099abcd\": {\"0x01\": \"0x54be\", \"0x00\": \"0xbe44\"}},"
            + "\"timestampMin\":\"7337\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithKnownAccounts,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "storage at address 0x000000000000000000000000000000000099abcd slot 0x0000000000000000000000000000000000000000000000000000000000000001 has been modified");
  }

  @Test
  public void knownAccount_storageHash_hasChanged_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    // timestamp within the min/max range
    when(header.getTimestamp()).thenReturn(7437L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    // block number within the min/max range
    final long headBlockNumber = 93L;
    when(blockchainQueries.headBlockNumber()).thenReturn(headBlockNumber);
    // non-matching storage hash
    final Address address = Address.fromHexString("0x000000000000000000000000000000000099abcd");
    when(blockchainQueries.storageRoot(address, headBlockNumber))
        .thenReturn(
            Optional.of(
                Hash.fromHexString(
                    "0xbeef460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")));

    final String jsonWithKnownAccounts =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\"0x00\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\","
            + "\"knownAccounts\": "
            + "{\"0x000000000000000000000000000000000099abcd\": \"0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470\"},"
            + "\"timestampMin\":\"7337\",\"timestampMax\":\"7447\"}],\"id\":1}";

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithKnownAccounts,
        USER_SPECIFIED_CONDITIONS_NOT_MET,
        "storage at address 0x000000000000000000000000000000000099abcd has been modified");
  }

  @Test
  public void maxConditionsExceeded_returnsError() throws JsonProcessingException {
    final BlockHeader header = mock(BlockHeader.class);
    // timestamp within the min/max range
    when(header.getTimestamp()).thenReturn(7437L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    // block number within the min/max range
    final long headBlockNumber = 93L;
    when(blockchainQueries.headBlockNumber()).thenReturn(headBlockNumber);

    final String jsonWithKnownAccountsTooLarge = getJsonWithTooManyStorageEntries();

    assertActualResponseIsErrorWithGivenMessage(
        jsonWithKnownAccountsTooLarge,
        EXCEEDS_RPC_MAX_CONDITIONS_SIZE,
        "maximum number of conditions exceeded");
  }

  @NotNull
  private String getJsonWithTooManyStorageEntries() {
    final StringBuilder stringBuilder = new StringBuilder("{");
    for (int i = 0; i <= MAX_CONDITIONS; i++) {
      stringBuilder.append("\"");
      stringBuilder.append(Bytes.of(i));
      stringBuilder.append("\"");
      stringBuilder.append(": ");
      stringBuilder.append("\"0x1234\"");
      if (i < MAX_CONDITIONS) {
        stringBuilder.append(",");
      }
    }
    stringBuilder.append("}");
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\""
            + VALID_TRANSACTION
            + "\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\","
            + "\"knownAccounts\": "
            + "{\"0x000000000000000000000000000000000099abcd\": "
            + stringBuilder
            + "},"
            + "\"timestampMin\":\"7337\",\"timestampMax\":\"7447\"}],\"id\":1}";
    return json;
  }

  @Test
  public void validTimestamp_validBlockNumber_isSentToTransactionPool()
      throws JsonProcessingException {
    when(transactionPool.addTransactionViaApi(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7437L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);

    final String jsonRequestString =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\""
            + VALID_TRANSACTION
            + "\",{\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new ObjectMapper().readValue(jsonRequestString, JsonRpcRequest.class));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0xbaabcc1bd699e7378451e4ce5969edb9bdcae76cb79bdacae793525c31e423c7");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addTransactionViaApi(any(Transaction.class));
  }

  @Test
  public void validKnownAccounts_isSentToTransactionPool() throws JsonProcessingException {
    when(transactionPool.addTransactionViaApi(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final BlockHeader header = mock(BlockHeader.class);
    when(header.getTimestamp()).thenReturn(7437L);
    when(blockchainQueries.headBlockHeader()).thenReturn(header);
    when(blockchainQueries.headBlockNumber()).thenReturn(93L);
    when(blockchainQueries.storageRoot(
            Address.fromHexString("0x000000000000000000000000000000000099abcd"), 93L))
        .thenReturn(
            Optional.of(
                Hash.fromHexString(
                    "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")));

    final String jsonRequestString =
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + METHOD_NAME
            + "\",\"params\":[\""
            + VALID_TRANSACTION
            + "\",{"
            + "\"knownAccounts\": "
            + "{\"0x000000000000000000000000000000000099abcd\": \"0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470\"},"
            + "\"blockNumberMin\":\"90\",\"blockNumberMax\":\"98\",\"timestampMin\":\"7339\",\"timestampMax\":\"7447\"}],\"id\":1}";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new ObjectMapper().readValue(jsonRequestString, JsonRpcRequest.class));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0xbaabcc1bd699e7378451e4ce5969edb9bdcae76cb79bdacae793525c31e423c7");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addTransactionViaApi(any(Transaction.class));
  }

  @Test
  public void noConditionsTransaction_isSentToTransactionPool() {
    when(transactionPool.addTransactionViaApi(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", METHOD_NAME, new String[] {VALID_TRANSACTION}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0xbaabcc1bd699e7378451e4ce5969edb9bdcae76cb79bdacae793525c31e423c7");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addTransactionViaApi(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches(METHOD_NAME);
  }

  private void assertActualResponseIsErrorWithGivenMessage(
      final String jsonRequestString, final JsonRpcError jsonRpcError, final String message)
      throws JsonProcessingException {
    verifyNoInteractions(transactionPool);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new ObjectMapper().readValue(jsonRequestString, JsonRpcRequest.class));
    final JsonRpcResponse actualResponse = method.response(request);
    // the JsonRpcError.data field gets reset by the enum
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(actualResponse.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    // test for the exact error message in data field
    assertThat(((JsonRpcErrorResponse) actualResponse).getError().getData()).isEqualTo(message);
  }
}
