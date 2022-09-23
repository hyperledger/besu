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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.rollup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.consensus.rollup.blockcreation.RollupMergeCoordinator;
import org.hyperledger.besu.consensus.rollup.blockcreation.RollupMergeCoordinator.PayloadCreationResult;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineCallListener;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.RollupCreatePayloadResult;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionValidationResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RollupCreatePayloadTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final Vertx vertx = Vertx.vertx();
  private static final long blockGasLimit = 20_000_000L;
  private static final Hash mockHash = Hash.hash(Bytes32.fromHexStringLenient("0x1337deadbeef"));
  private static final Hash mockPrevRandao = Hash.hash(Bytes32.random());
  private static final long mockBlockTimestamp = System.currentTimeMillis();
  private static final Address feeRecipient = Address.fromHexString("0x00112233aabbccddeeff");
  private static final PayloadIdentifier mockPayloadId =
      PayloadIdentifier.forPayloadParams(Hash.ZERO, 1337L);

  private static final KeyPair keyPair =
      keyPair("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");

  private final Transaction transaction1 =
      Transaction.builder()
          .chainId(new BigInteger("1", 10))
          .nonce(0)
          .value(Wei.of(10))
          .gasLimit(30000)
          .maxPriorityFeePerGas(Wei.of(2))
          .payload(Bytes.EMPTY.trimLeadingZeros())
          .maxFeePerGas(Wei.of(new BigInteger("5000000000", 10)))
          .gasPrice(null)
          .to(Address.fromHexString("0x000000000000000000000000000000000000aaaa"))
          .type(TransactionType.EIP1559)
          .signAndBuild(keyPair);

  final Transaction transaction2 =
      Transaction.builder()
          .chainId(new BigInteger("1", 10))
          .nonce(1)
          .value(Wei.of(10))
          .gasLimit(30000)
          .maxPriorityFeePerGas(Wei.of(2))
          .payload(Bytes.EMPTY.trimLeadingZeros())
          .maxFeePerGas(Wei.of(new BigInteger("5000000000", 10)))
          .gasPrice(null)
          .to(Address.fromHexString("0x000000000000000000000000000000000000aaaa"))
          .type(TransactionType.EIP1559)
          .signAndBuild(keyPair);

  final Transaction transaction3 =
      Transaction.builder()
          .chainId(new BigInteger("1", 10))
          .nonce(2)
          .value(Wei.of(10))
          .gasLimit(30000)
          .maxPriorityFeePerGas(Wei.of(2))
          .payload(Bytes.EMPTY.trimLeadingZeros())
          .maxFeePerGas(Wei.of(new BigInteger("5000000000", 10)))
          .gasPrice(null)
          .to(Address.fromHexString("0x000000000000000000000000000000000000aaab"))
          .type(TransactionType.EIP1559)
          .signAndBuild(keyPair);

  private RollupCreatePayload method;

  @Mock private ProtocolContext protocolContext;

  @Mock private RollupMergeCoordinator mergeCoordinator;

  @Mock private MutableBlockchain blockchain;

  @Before
  public void before() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method =
        new RollupCreatePayload(
            vertx,
            protocolContext,
            mergeCoordinator,
            new BlockResultFactory(),
            new EngineCallListener() {
              @Override
              public void executionEngineCalled() {}
            });
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("rollup_createPayloadV1");
  }

  @Test
  public void shouldReturnInvalidTerminalBlockIfParentRootHashDoesNotExist() {
    final var invalidParentHash = Hash.hash(Bytes32.fromHexStringLenient("0x1337deadbeef"));

    assertStatus(
        invalidParentHash,
        Collections.emptyList(),
        RollupCreatePayloadStatus.INVALID_TERMINAL_BLOCK);
  }

  @Test
  public void shouldReturnPayloadWhenBlockIsValid() {
    final BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .prevRandao(mockPrevRandao)
            .gasLimit(blockGasLimit)
            .timestamp(mockBlockTimestamp)
            .coinbase(feeRecipient)
            .buildHeader();
    final Block mockEmptyBlock =
        new Block(mockHeader, new BlockBody(List.of(transaction2), Collections.emptyList()));

    when(blockchain.getBlockHeader(mockHash)).thenReturn(Optional.of(mock(BlockHeader.class)));
    final var mockTransactionSelectionResults = mock(TransactionSelectionResults.class);
    when(mockTransactionSelectionResults.getInvalidTransactions())
        .thenReturn(
            List.of(
                new TransactionValidationResult(
                    transaction1,
                    ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW))));
    var blockCreationResult =
        new PayloadCreationResult(
            mockPayloadId,
            new BlockCreationResult(mockEmptyBlock, mockTransactionSelectionResults),
            new BlockValidator.Result(new BlockProcessingOutputs(null, Collections.emptyList())));
    when(mergeCoordinator.createBlock(any(), any(), any(), any(), any()))
        .thenReturn(blockCreationResult);

    final var result =
        assertStatus(
            mockHash,
            List.of(transaction1, transaction2, transaction3),
            RollupCreatePayloadStatus.PROCESSED);

    verify(mergeCoordinator)
        .createBlock(
            any(),
            eq(mockBlockTimestamp),
            eq(feeRecipient),
            eq(List.of(transaction1, transaction2, transaction3)),
            eq(mockPrevRandao));

    assertThat(result.getPayloadId()).isNotNull();
    assertThat(result.getPayloadId()).matches("0[xX][0-9a-fA-F]{16}");
    assertThat(result.getExecutionPayload()).isNotNull();
    assertThat(result.getExecutionPayload().getFeeRecipient())
        .isEqualTo(feeRecipient.toHexString());
    assertThat(result.getExecutionPayload().getPrevRandao())
        .isEqualTo(mockEmptyBlock.getHeader().getPrevRandao().get().toHexString());
    assertThat(result.getInvalidTransactions().size()).isEqualTo(1);
    assertThat(result.getInvalidTransactions().get(0).getTransaction())
        .isEqualTo(rlpEncode(transaction1));
    assertThat(result.getInvalidTransactions().get(0).getInvalidReason())
        .isEqualTo(TransactionInvalidReason.NONCE_TOO_LOW);
    assertThat(result.getUnprocessedTransactions()).isEqualTo(List.of(rlpEncode(transaction3)));
  }

  private RollupCreatePayloadResult assertStatus(
      final Hash parentHash,
      final List<Transaction> transactions,
      final RollupCreatePayloadStatus expectedStatus) {
    final RollupCreatePayloadResult result =
        fromSuccessResp(resp(parentHash, transactions, feeRecipient, mockBlockTimestamp));

    assertThat(result.getStatus()).isEqualTo(expectedStatus);
    return result;
  }

  private JsonRpcResponse resp(
      final Hash parentRootHash,
      final List<Transaction> transactions,
      final Address feeRecipient,
      final long timestamp) {
    final var rawTxs =
        transactions.stream().map(RollupCreatePayloadTest::rlpEncode).collect(Collectors.toList());
    var params =
        List.of(parentRootHash, rawTxs, mockPrevRandao, feeRecipient, String.valueOf(timestamp))
            .toArray();
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", RpcMethod.ROLLUP_CREATE_PAYLOAD.getMethodName(), params)));
  }

  static String rlpEncode(final Transaction transaction) {
    return TransactionEncoder.encodeOpaqueBytes(transaction).toHexString();
  }

  private RollupCreatePayloadResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(RollupCreatePayloadResult.class::cast)
        .get();
  }

  private static KeyPair keyPair(final String privateKey) {
    final SignatureAlgorithm signatureAlgorithm = SIGNATURE_ALGORITHM.get();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(privateKey)));
  }
}
