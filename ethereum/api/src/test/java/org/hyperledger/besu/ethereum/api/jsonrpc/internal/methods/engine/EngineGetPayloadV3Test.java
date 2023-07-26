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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV3;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(
    MockitoExtension.class) // mocks in parent class may not be used, throwing unnecessary stubbing
public class EngineGetPayloadV3Test extends AbstractEngineGetPayloadTest {

  private static final long CANCUN_AT = 31337L;

  public EngineGetPayloadV3Test() {
    super();
  }

  @BeforeEach
  @Override
  public void before() {
    lenient()
        .when(mergeContext.retrieveBlockById(mockPid))
        .thenReturn(Optional.of(mockBlockWithReceipts));
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    when(protocolSchedule.hardforkFor(any()))
        .thenReturn(Optional.of(new ScheduledProtocolSpec.Hardfork("shanghai", SHANGHAI_AT)));
    this.method =
        new EngineGetPayloadV3(
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
    assertThat(method.getName()).isEqualTo("engine_getPayloadV3");
  }

  @Override
  @Test
  public void shouldReturnBlockForKnownPayloadId() {

    BlockHeader cancunHeader =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .timestamp(CANCUN_AT + 1)
            .excessDataGas(DataGas.of(10L))
            .buildHeader();
    // should return withdrawals and excessGas for a post-cancun block
    PayloadIdentifier postCancunPid =
        PayloadIdentifier.forPayloadParams(
            Hash.ZERO,
            CANCUN_AT,
            Bytes32.random(),
            Address.fromHexString("0x42"),
            Optional.empty());

    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(1);
    Transaction blobTx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0xDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF")))
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.ONE))
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerDataGas(Optional.of(Wei.of(128)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .blobsWithCommitments(Optional.of(bwc))
            .versionedHashes(Optional.of(bwc.getVersionedHashes()))
            .createTransaction(senderKeys);
    TransactionReceipt blobReceipt = mock(TransactionReceipt.class);
    when(blobReceipt.getCumulativeGasUsed()).thenReturn(100L);
    BlockWithReceipts postCancunBlock =
        new BlockWithReceipts(
            new Block(
                cancunHeader,
                new BlockBody(
                    List.of(blobTx),
                    Collections.emptyList(),
                    Optional.of(Collections.emptyList()),
                    Optional.empty())),
            List.of(blobReceipt));

    when(mergeContext.retrieveBlockById(postCancunPid)).thenReturn(Optional.of(postCancunBlock));

    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V3.getMethodName(), postCancunPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV3.class);
              final EngineGetPayloadResultV3 res = (EngineGetPayloadResultV3) r.getResult();
              assertThat(res.getExecutionPayload().getWithdrawals()).isNotNull();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(cancunHeader.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(cancunHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
              // excessDataGas: QUANTITY, 256 bits
              String expectedQuantityOf10 = Bytes32.leftPad(Bytes.of(10)).toQuantityHexString();
              assertThat(res.getExecutionPayload().getExcessDataGas()).isNotEmpty();
              assertThat(res.getExecutionPayload().getExcessDataGas())
                  .isEqualTo(expectedQuantityOf10);
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V3.getMethodName();
  }
}
