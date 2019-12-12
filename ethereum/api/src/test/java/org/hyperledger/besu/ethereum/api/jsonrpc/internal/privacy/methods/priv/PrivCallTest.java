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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulatorResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivCallTest {

  private PrivCall method;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private PrivateTransactionSimulator privateTransactionSimulator;
  String privacyGroupId = "privacyGroupId";

  @Before
  public void setUp() {
    method = new PrivCall(blockchainQueries, privateTransactionSimulator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_call");
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenMissingToField() {
    final CallParameter callParameter = new JsonCallParameter("0x0", null, "0x0", "0x0", "0x0", "");
    final JsonRpcRequestContext request = ethCallRequest(privacyGroupId, callParameter, "latest");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing \"to\" field in call arguments");
  }

  @Test
  public void shouldReturnNullWhenProcessorReturnsEmpty() {
    final JsonRpcRequestContext request = ethCallRequest(privacyGroupId, callParameter(), "latest");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, null);

    when(privateTransactionSimulator.process(any(), any(), anyLong())).thenReturn(Optional.empty());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTransactionSimulator).process(any(), any(), anyLong());
  }

  @Test
  public void shouldAcceptRequestWhenMissingOptionalFields() {
    final CallParameter callParameter = new JsonCallParameter(null, "0x0", null, null, null, null);
    final JsonRpcRequestContext request = ethCallRequest(privacyGroupId, callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, BytesValue.of().toString());

    mockTransactionProcessorSuccessResult(BytesValue.of());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByFieldRecursively(expectedResponse);
    verify(privateTransactionSimulator).process(any(), eq(callParameter), anyLong());
  }

  @Test
  public void shouldReturnExecutionResultWhenExecutionIsSuccessful() {
    final JsonRpcRequestContext request = ethCallRequest(privacyGroupId, callParameter(), "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, BytesValue.of(1).toString());
    mockTransactionProcessorSuccessResult(BytesValue.of(1));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByFieldRecursively(expectedResponse);
    verify(privateTransactionSimulator).process(any(), eq(callParameter()), anyLong());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenLatest() {
    final JsonRpcRequestContext request = ethCallRequest(privacyGroupId, callParameter(), "latest");
    when(blockchainQueries.headBlockNumber()).thenReturn(11L);
    when(privateTransactionSimulator.process(any(), any(), anyLong())).thenReturn(Optional.empty());

    method.response(request);

    verify(privateTransactionSimulator).process(any(), any(), eq(11L));
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenEarliest() {
    final JsonRpcRequestContext request =
        ethCallRequest(privacyGroupId, callParameter(), "earliest");
    when(privateTransactionSimulator.process(any(), any(), anyLong())).thenReturn(Optional.empty());
    method.response(request);

    verify(privateTransactionSimulator).process(any(), any(), eq(0L));
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenSpecified() {
    final JsonRpcRequestContext request =
        ethCallRequest(privacyGroupId, callParameter(), Quantity.create(13L));
    when(privateTransactionSimulator.process(any(), any(), anyLong())).thenReturn(Optional.empty());

    method.response(request);

    verify(privateTransactionSimulator).process(any(), any(), eq(13L));
  }

  @Test
  public void shouldThrowCorrectExceptionWhenNoPrivacyGroupSpecified() {
    final JsonRpcRequestContext request =
        ethCallRequest(null, callParameter(), Quantity.create(13L));
    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  private CallParameter callParameter() {
    return new JsonCallParameter("0x0", "0x0", "0x0", "0x0", "0x0", "");
  }

  private JsonRpcRequestContext ethCallRequest(
      final String privacyGroupId,
      final CallParameter callParameter,
      final String blockNumberInHex) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "priv_call", new Object[] {privacyGroupId, callParameter, blockNumberInHex}));
  }

  private void mockTransactionProcessorSuccessResult(final BytesValue output) {
    final PrivateTransactionSimulatorResult result = mock(PrivateTransactionSimulatorResult.class);

    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getOutput()).thenReturn(output);
    when(privateTransactionSimulator.process(any(), any(), anyLong()))
        .thenReturn(Optional.of(result));
  }
}
