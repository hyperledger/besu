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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.Address.fromHexString;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.clique.CliqueBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.ethereum.api.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
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

import org.assertj.core.api.AssertionsForClassTypes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CliqueGetSignersAtHashTest {

  private CliqueGetSignersAtHash method;
  private BlockHeader blockHeader;
  private List<Address> validators;
  private List<String> validatorsAsStrings;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private VoteTallyCache voteTallyCache;
  @Mock private BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata;
  @Mock private VoteTally voteTally;

  public static final String BLOCK_HASH =
      "0xe36a3edf0d8664002a72ef7c5f8e271485e7ce5c66455a07cb679d855818415f";

  @Before
  public void setup() {
    method = new CliqueGetSignersAtHash(blockchainQueries, voteTallyCache, new JsonRpcParameter());

    final byte[] genesisBlockExtraData =
        Hex.decode(
            "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    blockHeader =
        new BlockHeaderTestFixture()
            .blockHeaderFunctions(new CliqueBlockHeaderFunctions())
            .extraData(BytesValue.wrap(genesisBlockExtraData))
            .buildHeader();

    validators =
        asList(
            fromHexString("0x42eb768f2244c8811c63729a21a3569731535f06"),
            fromHexString("0x7ffc57839b00206d1ad20c69a1981b489f772031"),
            fromHexString("0xb279182d99e65703f0076e4812653aab85fca0f0"));
    validatorsAsStrings = validators.stream().map(Object::toString).collect(toList());
  }

  @Test
  public void returnsMethodName() {
    assertThat(method.getName()).isEqualTo("clique_getSignersAtHash");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void failsWhenNoParam() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSignersAtHash", new Object[] {});

    final Throwable thrown = AssertionsForClassTypes.catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsForBlockHash() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSignersAtHash", new Object[] {BLOCK_HASH});

    when(blockchainQueries.blockByHash(Hash.fromHexString(BLOCK_HASH)))
        .thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);
    when(voteTallyCache.getVoteTallyAfterBlock(blockHeader)).thenReturn(voteTally);
    when(voteTally.getValidators()).thenReturn(validators);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    assertThat(response.getResult()).isEqualTo(validatorsAsStrings);
  }

  @Test
  public void failsOnInvalidBlockHash() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {BLOCK_HASH});

    when(blockchainQueries.blockByHash(Hash.fromHexString(BLOCK_HASH)))
        .thenReturn(Optional.empty());

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);
    assertThat(response.getError().name()).isEqualTo(JsonRpcError.INTERNAL_ERROR.name());
  }
}
