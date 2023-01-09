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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV2;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineGetPayloadV2Test extends AbstractEngineGetPayloadTest {

  public EngineGetPayloadV2Test() {
    super(EngineGetPayloadV2::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_getPayloadV2");
  }

  @Override
  @Test
  public void shouldReturnBlockForKnownPayloadId() {
    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(0));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(mockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnBlockWithCorrectValue() {
    // Generate block with two transactions
    final long baseFee = 15;
    final long maxFee = 20;
    final Transaction tx1 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final Transaction tx2 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(2)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final Transaction tx3 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(10)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final TransactionReceipt receipt1 =
        new TransactionReceipt(Hash.EMPTY_TRIE_HASH, 71, Collections.emptyList(), Optional.empty());
    final TransactionReceipt receipt2 =
        new TransactionReceipt(
            Hash.EMPTY_TRIE_HASH, 143, Collections.emptyList(), Optional.empty());
    final TransactionReceipt receipt3 =
        new TransactionReceipt(
            Hash.EMPTY_TRIE_HASH, 214, Collections.emptyList(), Optional.empty());
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .baseFeePerGas(Wei.of(baseFee))
            .buildHeader();
    final Block block =
        new Block(blockHeader, new BlockBody(List.of(tx1, tx2, tx3), Collections.emptyList()));
    // Generate pid
    final PayloadIdentifier pid =
        PayloadIdentifier.forPayloadParams(
            Hash.ZERO, 1337L, Bytes32.random(), Address.fromHexString("0x43"));
    when(mergeContext.retrieveBlockById(pid))
        .thenReturn(
            Optional.of(new BlockWithReceipts(block, List.of(receipt1, receipt2, receipt3))));
    // Test get payload
    final var resp = resp(RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName(), pid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(EngineGetPayloadResultV2.class);
              final EngineGetPayloadResultV2 res = (EngineGetPayloadResultV2) r.getResult();
              assertThat(res.getExecutionPayload().getHash())
                  .isEqualTo(blockHeader.getHash().toString());
              // Block value = 71 * 1 + 143 * 2 + 214 * 5 = 1427
              assertThat(res.getBlockValue()).isEqualTo(Quantity.create(1427));
              assertThat(res.getExecutionPayload().getPrevRandao())
                  .isEqualTo(blockHeader.getPrevRandao().map(Bytes32::toString).orElse(""));
            });
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected String getMethodName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V2.getMethodName();
  }
}
