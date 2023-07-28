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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractEngineGetPayloadTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair senderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();

  @FunctionalInterface
  interface MethodFactory {
    AbstractEngineGetPayload create(
        final Vertx vertx,
        final ProtocolContext protocolContext,
        final MergeMiningCoordinator mergeCoordinator,
        final BlockResultFactory ethPeers,
        final EngineCallListener engineCallListener);
  }

  private final MethodFactory methodFactory;
  protected AbstractEngineGetPayload method;

  public AbstractEngineGetPayloadTest(final MethodFactory methodFactory) {
    this.methodFactory = methodFactory;
  }

  public AbstractEngineGetPayloadTest() {
    this.methodFactory = null;
  }

  protected static final Vertx vertx = Vertx.vertx();
  protected static final BlockResultFactory factory = new BlockResultFactory();
  protected static final PayloadIdentifier mockPid =
      PayloadIdentifier.forPayloadParams(
          Hash.ZERO, 1337L, Bytes32.random(), Address.fromHexString("0x42"), Optional.empty());
  protected static final BlockHeader mockHeader =
      new BlockHeaderTestFixture().prevRandao(Bytes32.random()).buildHeader();
  private static final Block mockBlock =
      new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));
  protected static final BlockWithReceipts mockBlockWithReceipts =
      new BlockWithReceipts(mockBlock, Collections.emptyList());
  private static final Block mockBlockWithWithdrawals =
      new Block(
          mockHeader,
          new BlockBody(
              Collections.emptyList(),
              Collections.emptyList(),
              Optional.of(Collections.emptyList()),
              Optional.empty()));
  private static final Block mockBlockWithDeposits =
      new Block(
          mockHeader,
          new BlockBody(
              Collections.emptyList(),
              Collections.emptyList(),
              Optional.empty(),
              Optional.of(Collections.emptyList())));
  protected static final BlockWithReceipts mockBlockWithReceiptsAndWithdrawals =
      new BlockWithReceipts(mockBlockWithWithdrawals, Collections.emptyList());

  protected static final BlockWithReceipts mockBlockWithReceiptsAndDeposits =
      new BlockWithReceipts(mockBlockWithDeposits, Collections.emptyList());

  @Mock protected ProtocolContext protocolContext;

  @Mock protected MergeContext mergeContext;
  @Mock protected MergeMiningCoordinator mergeMiningCoordinator;

  @Mock protected EngineCallListener engineCallListener;

  @Mock protected ProtocolSchedule protocolSchedule;

  protected static final long SHANGHAI_AT = 1337L;

  @BeforeEach
  public void before() {
    when(mergeContext.retrieveBlockById(mockPid)).thenReturn(Optional.of(mockBlockWithReceipts));
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    when(protocolSchedule.hardforkFor(any()))
        .thenReturn(Optional.of(new ScheduledProtocolSpec.Hardfork("shanghai", SHANGHAI_AT)));
    this.method =
        methodFactory.create(
            vertx, protocolContext, mergeMiningCoordinator, factory, engineCallListener);
  }

  @Test
  public abstract void shouldReturnExpectedMethodName();

  @Test
  public abstract void shouldReturnBlockForKnownPayloadId();

  @Test
  public void shouldFailForUnknownPayloadId() {
    final var resp =
        resp(
            getMethodName(),
            PayloadIdentifier.forPayloadParams(
                Hash.ZERO, 0L, Bytes32.random(), Address.fromHexString("0x42"), Optional.empty()));
    assertThat(resp).isInstanceOf(JsonRpcErrorResponse.class);
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  abstract String getMethodName();

  protected JsonRpcResponse resp(final String methodName, final PayloadIdentifier pid) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", methodName, new Object[] {pid.serialize()})));
  }
}
