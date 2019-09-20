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
package org.hyperledger.besu.consensus.ibft.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IbftDiscardValidatorVoteTest {
  private final VoteProposer voteProposer = mock(VoteProposer.class);
  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();
  private final String IBFT_METHOD = "ibft_discardValidatorVote";
  private final String JSON_RPC_VERSION = "2.0";
  private IbftDiscardValidatorVote method;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    method = new IbftDiscardValidatorVote(voteProposer, jsonRpcParameter);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(IBFT_METHOD);
  }

  @Test
  public void exceptionWhenNoParamsSupplied() {
    final JsonRpcRequest request = requestWithParams();

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Missing required json rpc parameter at index 0");

    method.response(request);
  }

  @Test
  public void exceptionWhenInvalidAddressParameterSupplied() {
    final JsonRpcRequest request = requestWithParams("InvalidAddress");

    expectedException.expect(InvalidJsonRpcParameters.class);
    expectedException.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);
  }

  @Test
  public void discardValidator() {
    final Address parameterAddress = Address.fromHexString("1");
    final JsonRpcRequest request = requestWithParams(parameterAddress);

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), true);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);

    verify(voteProposer).discard(parameterAddress);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, IBFT_METHOD, params);
  }
}
