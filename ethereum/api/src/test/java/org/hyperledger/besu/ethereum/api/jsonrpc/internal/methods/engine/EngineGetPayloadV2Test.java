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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.UNSUPPORTED_FORK;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.PayloadWrapper;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV2;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineGetPayloadV2Test extends AbstractEngineGetPayloadTest {

  public EngineGetPayloadV2Test() {
    super();
  }

  @BeforeEach
  @Override
  public void before() {
    super.before();
    lenient().when(mergeContext.retrievePayloadById(mockPid)).thenReturn(Optional.of(mockPayload));
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    this.method =
        new EngineGetPayloadV2(
            vertx,
            protocolContext,
            mergeMiningCoordinator,
            factory,
            engineCallListener,
            protocolSchedule);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getPayloadV2");
  }

  @Override
  @Test
  public void shouldReturnBlockForKnownPayloadId() {
    // should return withdrawals for a post-Shanghai block
    when(mergeContext.retrievePayloadById(mockPid))
        .thenReturn(Optional.of(mockPayloadWithWithdrawals));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNotNull();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(mockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnExecutionPayloadWithoutWithdrawals_PreShanghaiBlock() {
    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNull();
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnUnsupportedForkIfBlockTimestampIsAfterCancunMilestone() {
    // Cancun starts at timestamp 30
    final BlockHeader mockHeader = new BlockHeaderTestFixture().timestamp(31L).buildHeader();
    final Block mockBlock =
        new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));
    final BlockWithReceipts mockBlockWithReceipts =
        new BlockWithReceipts(mockBlock, Collections.emptyList());
    final PayloadWrapper mockPayload = new PayloadWrapper(mockPid, mockBlockWithReceipts);

    when(mergeContext.retrievePayloadById(mockPid)).thenReturn(Optional.of(mockPayload));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);

    final JsonRpcError jsonRpcError =
        Optional.of(resp)
            .map(JsonRpcErrorResponse.class::cast)
            .map(JsonRpcErrorResponse::getError)
            .get();
    assertThat(jsonRpcError.getCode()).isEqualTo(UNSUPPORTED_FORK.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName();
  }
}
