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
package org.hyperledger.besu.consensus.common.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class AbstractVoteProposerMethodTest {

  private final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
  private final VoteProvider voteProvider = mock(VoteProvider.class);
  private final String JSON_RPC_VERSION = "2.0";

  protected abstract AbstractVoteProposerMethod getMethod();

  protected abstract String getMethodName();

  protected ValidatorProvider getValidatorProvider() {
    return validatorProvider;
  }

  @Test
  public void testConversionFromVoteTypeToBoolean() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(JSON_RPC_VERSION, getMethodName(), new Object[] {}));

    when(validatorProvider.getVoteProviderAtHead()).thenReturn(Optional.of(voteProvider));
    when(voteProvider.getProposals())
        .thenReturn(
            ImmutableMap.of(
                Address.fromHexString("1"),
                VoteType.ADD,
                Address.fromHexString("2"),
                VoteType.DROP));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            ImmutableMap.of(
                "0x0000000000000000000000000000000000000001",
                true,
                "0x0000000000000000000000000000000000000002",
                false));

    final JsonRpcResponse response = getMethod().response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void methodNotEnabledWhenNoVoteProvider() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(JSON_RPC_VERSION, getMethodName(), new Object[] {}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.METHOD_NOT_ENABLED);
    when(validatorProvider.getVoteProviderAtHead()).thenReturn(Optional.empty());

    final JsonRpcResponse response = getMethod().response(request);

    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}
