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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobsBundleV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV6110;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(
    MockitoExtension.class) // mocks in parent class may not be used, throwing unnecessary stubbing
public class EngineGetPayloadV6110Test extends AbstractEngineGetPayloadTest {

  private static final long EIP_6110_AT = 31337L;

  public EngineGetPayloadV6110Test() {
    super(EngineGetPayloadV6110::new);
  }

  @BeforeEach
  @Override
  public void before() {
    super.before();
    this.method =
        new EngineGetPayloadV6110(
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
    assertThat(method.getName()).isEqualTo("engine_getPayloadV6110");
  }

  @Override
  @Test
  public void shouldReturnBlockForKnownPayloadId() {

    BlockHeader eip6110Header =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .timestamp(EIP_6110_AT + 1)
            .excessDataGas(DataGas.of(10L))
            .buildHeader();
    // should return withdrawals and excessGas for a post-eip6110 block
    PayloadIdentifier postEip6110Pid =
        PayloadIdentifier.forPayloadParams(
            Hash.ZERO,
            EIP_6110_AT,
            Bytes32.random(),
            Address.fromHexString("0x42"),
            Optional.empty());

    BlockWithReceipts postEip6110Block =
        new BlockWithReceipts(
            new Block(
                eip6110Header,
                new BlockBody(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Optional.of(Collections.emptyList()),
                    Optional.empty())),
            Collections.emptyList());

    when(mergeContext.retrieveBlockById(postEip6110Pid)).thenReturn(Optional.of(postEip6110Block));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V6110.getMethodName(), postEip6110Pid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV6110.class);
              final EngineGetPayloadResultV6110 res = (EngineGetPayloadResultV6110) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNotNull();
              assertThat(res.getExecutionPayload().getDeposits()).isNotNull();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(eip6110Header.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(eip6110Header.getPrevRandao().map(Bytes32::toString).orElse(""));
              // excessDataGas: QUANTITY, 256 bits
              String expectedQuantityOf10 = Bytes32.leftPad(Bytes.of(10)).toQuantityHexString();
              assertThat(res.getExecutionPayload().getExcessDataGas()).isNotEmpty();
              assertThat(res.getExecutionPayload().getExcessDataGas())
                  .isEqualTo(expectedQuantityOf10);
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void blobsBundleV1MustHaveSameNumberOfElements() {
    String actualMessage =
        assertThrows(
                InvalidParameterException.class,
                () -> new BlobsBundleV1(List.of(""), List.of(""), List.of()))
            .getMessage();
    final String expectedMessage = "There must be an equal number of blobs, commitments and proofs";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }

  @Test
  public void shouldReturnUnsupportedFork() {
    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V6110.getMethodName(), mockPid);

    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V6110.getMethodName();
  }
}
