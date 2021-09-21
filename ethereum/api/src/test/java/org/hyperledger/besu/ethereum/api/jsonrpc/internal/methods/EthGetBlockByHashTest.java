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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByHashTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Mock private BlockchainQueries blockchainQueries;
  private final BlockResultFactory blockResult = new BlockResultFactory();
  private EthGetBlockByHash method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_getBlockByHash";
  private final String ZERO_HASH = String.valueOf(Hash.ZERO);

  @Before
  public void setUp() {
    method = new EthGetBlockByHash(blockchainQueries, blockResult);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void exceptionWhenNoParamsSupplied() {
    final JsonRpcRequestContext request = requestWithParams();

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenNoHashSupplied() {
    final JsonRpcRequestContext request = requestWithParams("false");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenNoBoolSupplied() {
    final JsonRpcRequestContext request = requestWithParams(ZERO_HASH);

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 1");

    method.response(request);

    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenHashParamInvalid() {
    final JsonRpcRequestContext request = requestWithParams("hash", "true");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenBoolParamInvalid() {
    final JsonRpcRequestContext request = requestWithParams(ZERO_HASH, "maybe");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 1");

    method.response(request);

    verifyNoMoreInteractions(blockchainQueries);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }
}
