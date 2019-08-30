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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea.EeaGetTransactionCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea.EeaPrivateNonceProvider;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;

public class EeaGetTransactionCountTest {

  private EeaPrivateNonceProvider nonceProvider = mock(EeaPrivateNonceProvider.class);
  private JsonRpcRequest request;

  private final String privateFrom = "thePrivateFromKey";
  private final String[] privateFor = new String[] {"first", "second", "third"};
  private final Address address = Address.fromHexString("55");

  @Before
  public void setup() {
    final Object[] jsonBody = new Object[] {address.toString(), privateFrom, privateFor};
    request = new JsonRpcRequest("2.0", "eea_getTransactionCount", jsonBody);
  }

  @Test
  public void validRequestProducesExpectedNonce() {
    final long reportedNonce = 8L;
    final EeaGetTransactionCount method =
        new EeaGetTransactionCount(new JsonRpcParameter(), nonceProvider);

    when(nonceProvider.determineNonce(privateFrom, privateFor, address)).thenReturn(reportedNonce);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);

    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    int returnedValue = Integer.decode((String) successResponse.getResult());
    assertThat(returnedValue).isEqualTo(reportedNonce);
  }

  @Test
  public void nonceProviderThrowsRuntimeExceptionProducesErrorResponse() {
    final EeaGetTransactionCount method =
        new EeaGetTransactionCount(new JsonRpcParameter(), nonceProvider);

    when(nonceProvider.determineNonce(privateFrom, privateFor, address))
        .thenThrow(RuntimeException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void nonceProviderThrowsAnExceptionProducesErrorResponse() {
    final EeaGetTransactionCount method =
        new EeaGetTransactionCount(new JsonRpcParameter(), nonceProvider);

    when(nonceProvider.determineNonce(privateFrom, privateFor, address))
        .thenThrow(RuntimeException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }
}
