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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.FIND_PRIVACY_GROUP_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.DefaultPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class PrivDebugGetStateRootTest {

  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final PrivacyGroup PRIVACY_GROUP =
      new PrivacyGroup(
          "1",
          PrivacyGroup.Type.LEGACY,
          "group",
          "Test group",
          Collections.singletonList(ENCLAVE_PUBLIC_KEY));

  private PrivDebugGetStateRoot method;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private final PrivacyController privacyController = mock(DefaultPrivacyController.class);

  @Before
  public void setUp() {
    method =
        new PrivDebugGetStateRoot(blockchainQueries, enclavePublicKeyProvider, privacyController);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_debugGetStateRoot");
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenNoPrivacyGroup() {
    final JsonRpcRequestContext request = request(null, "latest");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void shouldReturnErrorIfPrivacyGroupDoesNotExist() {
    when(privacyController.findPrivacyGroup(anyList(), anyString()))
        .thenReturn(new PrivacyGroup[] {});
    final JsonRpcResponse response = method.response(request("invalid_group", "latest"));
    assertThat(response.getType()).isEqualByComparingTo(JsonRpcResponseType.ERROR);
    assertThat(((JsonRpcErrorResponse) response).getError().getMessage())
        .contains(FIND_PRIVACY_GROUP_ERROR.getMessage());
  }

  @Test
  public void shouldReturnErrorIfUnableToFindStateRoot() {
    when(privacyController.findPrivacyGroup(anyList(), anyString()))
        .thenReturn(new PrivacyGroup[] {PRIVACY_GROUP});
    when(privacyController.getStateRootByBlockNumber(anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) method.response(request(ENCLAVE_PUBLIC_KEY, "latest"));

    assertThat(response.getError().getMessage()).contains(INVALID_PARAMS.getMessage());
  }

  @Test
  public void shouldReturnSuccessWhenValidPrivacyGroupAndStateRoot() {
    final Hash hash = Hash.EMPTY_LIST_HASH;

    when(privacyController.findPrivacyGroup(anyList(), anyString()))
        .thenReturn(new PrivacyGroup[] {PRIVACY_GROUP});
    when(privacyController.getStateRootByBlockNumber(anyString(), anyString(), anyLong()))
        .thenReturn(Optional.of(hash));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) method.response(request(ENCLAVE_PUBLIC_KEY, "latest"));
    final Hash result = (Hash) response.getResult();

    assertThat(result).isEqualTo(hash);
  }

  private JsonRpcRequestContext request(final String privacyGroupId, final String params) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", method.getName(), new Object[] {privacyGroupId, new BlockParameter(params)}));
  }
}
