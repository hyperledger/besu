/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Collections;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineGetPayloadTest {

  private EngineGetPayload method;
  private static final Vertx vertx = Vertx.vertx();
  private static final BlockResultFactory factory = new BlockResultFactory();
  private static final PayloadIdentifier mockPid =
      PayloadIdentifier.forPayloadParams(Hash.ZERO, 1337L);
  private static final BlockHeader mockHeader =
      new BlockHeaderTestFixture().prevRandao(Bytes32.random()).buildHeader();
  private static final Block mockBlock =
      new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));

  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Before
  public void before() {
    when(mergeContext.retrieveBlockById(mockPid)).thenReturn(Optional.of(mockBlock));
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    this.method = new EngineGetPayload(vertx, protocolContext, factory);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_getPayloadV1");
  }

  @Test
  public void shouldReturnBlockForKnownPayloadId() {
    var resp = resp(mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResult.class);
              EngineGetPayloadResult res = (EngineGetPayloadResult) r.getResult();
              assertThat(res.getHash()).isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getPrevRandao())
                  .isEqualTo(mockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
  }

  @Test
  public void shouldFailForUnknownPayloadId() {
    var resp = resp(PayloadIdentifier.forPayloadParams(Hash.ZERO, 0L));
    assertThat(resp).isInstanceOf(JsonRpcErrorResponse.class);
  }

  private JsonRpcResponse resp(final PayloadIdentifier pid) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_GET_PAYLOAD.getMethodName(),
                new Object[] {pid.serialize()})));
  }
}
