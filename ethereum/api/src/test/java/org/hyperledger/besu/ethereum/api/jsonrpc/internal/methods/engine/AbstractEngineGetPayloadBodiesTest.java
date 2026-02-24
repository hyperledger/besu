/*
 * Copyright contributors to Besu.
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV2;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotRead;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractEngineGetPayloadBodiesTest {

  private final MethodFactory methodFactory;

  protected static final Vertx vertx = Vertx.vertx();
  protected static final BlockResultFactory blockResultFactory = new BlockResultFactory();

  protected AbstractEngineGetPayloadBodies method;
  @Mock protected ProtocolContext protocolContext;
  @Mock protected EngineCallListener engineCallListener;
  @Mock protected org.hyperledger.besu.ethereum.chain.MutableBlockchain blockchain;

  @FunctionalInterface
  interface MethodFactory {
    AbstractEngineGetPayloadBodies create(
        final Vertx vertx,
        final ProtocolContext protocolContext,
        final BlockResultFactory blockResultFactory,
        final EngineCallListener engineCallListener);
  }

  public AbstractEngineGetPayloadBodiesTest(final MethodFactory methodFactory) {
    this.methodFactory = methodFactory;
  }

  @BeforeEach
  public void before() {
    lenient().when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method =
        spy(methodFactory.create(vertx, protocolContext, blockResultFactory, engineCallListener));
  }

  protected EngineGetPayloadBodiesResultV2 fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(RpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EngineGetPayloadBodiesResultV2.class::cast)
        .get();
  }

  protected static BlockAccessList createSampleBlockAccessList() {
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

  protected static String encodeBlockAccessList(final BlockAccessList blockAccessList) {
    final var output = new org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput();
    blockAccessList.writeTo(output);
    return output.encoded().toHexString();
  }
}
