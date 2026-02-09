/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineTestSupport.fromErrorResp;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_RANGE_REQUEST_TOO_LARGE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV2;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotRead;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineGetPayloadBodiesByHashV2Test {
  private EngineGetPayloadBodiesByHashV2 method;
  private static final Vertx vertx = Vertx.vertx();
  private static final BlockResultFactory blockResultFactory = new BlockResultFactory();
  @Mock private ProtocolContext protocolContext;
  @Mock private EngineCallListener engineCallListener;
  @Mock private org.hyperledger.besu.ethereum.chain.MutableBlockchain blockchain;

  @BeforeEach
  public void before() {
    lenient().when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method =
        spy(
            new EngineGetPayloadBodiesByHashV2(
                vertx, protocolContext, blockResultFactory, engineCallListener));
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getPayloadBodiesByHashV2");
  }

  @Test
  public void shouldReturnBlockAccessListWhenAvailable() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());
    final BlockAccessList blockAccessList = createSampleBlockAccessList();
    final String encodedBlockAccessList = encodeBlockAccessList(blockAccessList);

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.of(blockAccessList));

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList())
        .isEqualTo(encodedBlockAccessList);
  }

  @Test
  public void shouldReturnNullBlockAccessListForPreAmsterdamBlock() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList()).isNull();
  }

  @Test
  public void shouldReturnNullBlockAccessListWhenPruned() {
    final SignatureAlgorithm sig = SignatureAlgorithmFactory.getInstance();
    final Hash blockHash = Hash.wrap(Bytes32.random());
    final BlockBody blockBody =
        new BlockBody(
            List.of(new TransactionTestFixture().createTransaction(sig.generateKeyPair())),
            Collections.emptyList());

    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.empty());

    final var resp = resp(new Hash[] {blockHash});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(1);
    assertThat(result.getPayloadBodies().get(0).getBlockAccessList()).isNull();
  }

  @Test
  public void shouldReturnEmptyPayloadBodiesWithEmptyHash() {
    final var resp = resp(new Hash[] {});
    final EngineGetPayloadBodiesResultV2 result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().isEmpty()).isTrue();
  }

  @Test
  public void shouldReturnNullForUnknownHashes() {
    final Hash blockHash1 = Hash.wrap(Bytes32.random());
    final Hash blockHash2 = Hash.wrap(Bytes32.random());
    final Hash blockHash3 = Hash.wrap(Bytes32.random());
    final var resp = resp(new Hash[] {blockHash1, blockHash2, blockHash3});
    final var result = fromSuccessResp(resp);
    assertThat(result.getPayloadBodies().size()).isEqualTo(3);
    assertThat(result.getPayloadBodies().get(0)).isNull();
    assertThat(result.getPayloadBodies().get(1)).isNull();
    assertThat(result.getPayloadBodies().get(2)).isNull();
  }

  @Test
  public void shouldReturnErrorWhenRequestExceedsPermittedNumberOfBlocks() {
    final Hash blockHash1 = Hash.wrap(Bytes32.random());
    final Hash blockHash2 = Hash.wrap(Bytes32.random());
    final Hash[] hashes = new Hash[] {blockHash1, blockHash2};

    doReturn(1).when(method).getMaxRequestBlocks();

    final JsonRpcResponse resp = resp(hashes);
    final var result = fromErrorResp(resp);
    assertThat(result.getCode()).isEqualTo(INVALID_RANGE_REQUEST_TOO_LARGE.getCode());
  }

  private JsonRpcResponse resp(final Hash[] hashes) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_GET_PAYLOAD_BODIES_BY_HASH_V2.getMethodName(),
                new Object[] {hashes})));
  }

  private EngineGetPayloadBodiesResultV2 fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(RpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineGetPayloadBodiesResultV2.class::cast)
        .get();
  }

  private static BlockAccessList createSampleBlockAccessList() {
    final Address address = Address.fromHexString("0x0000000000000000000000000000000000000001");
    final StorageSlotKey slotKey = new StorageSlotKey(UInt256.ONE);
    final SlotChanges slotChanges =
        new SlotChanges(slotKey, List.of(new StorageChange(0, UInt256.valueOf(2))));
    return new BlockAccessList(
        List.of(
            new AccountChanges(
                address,
                List.of(slotChanges),
                List.of(new SlotRead(slotKey)),
                List.of(new BalanceChange(0, Wei.ONE)),
                List.of(),
                List.of())));
  }

  private static String encodeBlockAccessList(final BlockAccessList blockAccessList) {
    final var output = new org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput();
    blockAccessList.writeTo(output);
    return output.encoded().toHexString();
  }
}
