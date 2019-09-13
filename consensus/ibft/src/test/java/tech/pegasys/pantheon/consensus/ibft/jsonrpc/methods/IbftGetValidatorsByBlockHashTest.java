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
package tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods;

import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftGetValidatorsByBlockHashTest {

  private static final String ETH_METHOD = "ibft_getValidatorsByBlockHash";
  private static final String JSON_RPC_VERSION = "2.0";
  private static final String ZERO_HASH = String.valueOf(Hash.ZERO);

  @Mock private Blockchain blockchain;
  @Mock private BlockHeader blockHeader;
  @Mock private IbftBlockInterface ibftBlockInterface;
  @Mock private JsonRpcRequest request;

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private IbftGetValidatorsByBlockHash method;

  @Before
  public void setUp() {
    method = new IbftGetValidatorsByBlockHash(blockchain, ibftBlockInterface, parameters);
  }

  @Test
  public void nameShouldBeCorrect() {
    Assertions.assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnListOfValidatorsFromBlock() {
    when(blockchain.getBlockHeader(Hash.ZERO)).thenReturn(Optional.of(blockHeader));
    final List<Address> addresses = Collections.singletonList(Address.ID);
    final List<String> expectedOutput = Collections.singletonList(Address.ID.toString());
    when(ibftBlockInterface.validatorsInBlock(blockHeader)).thenReturn(addresses);
    request = requestWithParams(ZERO_HASH);
    JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    Assertions.assertThat(response.getResult()).isEqualTo(expectedOutput);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
