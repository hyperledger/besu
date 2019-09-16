/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.Address.fromHexString;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.ethereum.api.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CliqueGetSignersTest {
  private CliqueGetSigners method;
  private BlockHeader blockHeader;
  private List<Address> validators;
  private List<String> validatorAsStrings;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private VoteTallyCache voteTallyCache;
  @Mock private BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata;
  @Mock private VoteTally voteTally;

  @Before
  public void setup() {
    method = new CliqueGetSigners(blockchainQueries, voteTallyCache, new JsonRpcParameter());

    final String genesisBlockExtraData =
        "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    final BytesValue bufferToInject = BytesValue.fromHexString(genesisBlockExtraData);
    final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
    blockHeader = blockHeaderTestFixture.extraData(bufferToInject).buildHeader();

    validators =
        asList(
            fromHexString("0x42eb768f2244c8811c63729a21a3569731535f06"),
            fromHexString("0x7ffc57839b00206d1ad20c69a1981b489f772031"),
            fromHexString("0xb279182d99e65703f0076e4812653aab85fca0f0"));
    validatorAsStrings = validators.stream().map(Object::toString).collect(toList());
  }

  @Test
  public void returnsMethodName() {
    assertThat(method.getName()).isEqualTo("clique_getSigners");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsWhenNoParam() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {});

    when(blockchainQueries.headBlockNumber()).thenReturn(3065995L);
    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);
    when(voteTallyCache.getVoteTallyAfterBlock(blockHeader)).thenReturn(voteTally);
    when(voteTally.getValidators()).thenReturn(validators);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    assertThat(response.getResult()).isEqualTo(validatorAsStrings);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsForBlockNumber() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x2EC88B"});

    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);
    when(voteTallyCache.getVoteTallyAfterBlock(blockHeader)).thenReturn(voteTally);
    when(voteTally.getValidators()).thenReturn(validators);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    assertThat(response.getResult()).isEqualTo(validatorAsStrings);
  }

  @Test
  public void failsOnInvalidBlockNumber() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x1234"});

    when(blockchainQueries.blockByNumber(4660)).thenReturn(Optional.empty());

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);
    assertThat(response.getError().name()).isEqualTo(JsonRpcError.INTERNAL_ERROR.name());
  }
}
