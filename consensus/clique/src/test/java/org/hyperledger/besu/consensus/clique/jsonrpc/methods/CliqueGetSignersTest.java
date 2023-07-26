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
import static org.hyperledger.besu.datatypes.Address.fromHexString;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CliqueGetSignersTest {
  private CliqueGetSigners method;
  private BlockHeader blockHeader;
  private List<Address> validators;
  private List<String> validatorAsStrings;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ValidatorProvider validatorProvider;
  @Mock private BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata;

  @BeforeEach
  public void setup() {
    method = new CliqueGetSigners(blockchainQueries, validatorProvider);

    final String genesisBlockExtraData =
        "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    final Bytes bufferToInject = Bytes.fromHexString(genesisBlockExtraData);
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
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {}));

    when(blockchainQueries.headBlockNumber()).thenReturn(3065995L);
    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);
    when(validatorProvider.getValidatorsAfterBlock(blockHeader)).thenReturn(validators);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    assertThat(response.getResult()).isEqualTo(validatorAsStrings);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void returnsValidatorsForBlockNumber() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x2EC88B"}));

    when(blockchainQueries.blockByNumber(3065995L)).thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);
    when(validatorProvider.getValidatorsAfterBlock(blockHeader)).thenReturn(validators);

    final JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) method.response(request);
    assertThat(response.getResult()).isEqualTo(validatorAsStrings);
  }

  @Test
  public void failsOnInvalidBlockNumber() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "clique_getSigners", new Object[] {"0x1234"}));

    when(blockchainQueries.blockByNumber(4660)).thenReturn(Optional.empty());

    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) method.response(request);
    assertThat(response.getErrorType()).isEqualTo(RpcErrorType.INTERNAL_ERROR);
  }
}
