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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.ACCEPTED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameterTestFixture.DEPOSIT_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameterTestFixture.WITHDRAWAL_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractEngineNewPayloadTest {

  @FunctionalInterface
  interface MethodFactory {
    AbstractEngineNewPayload create(
        final Vertx vertx,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final MergeMiningCoordinator mergeCoordinator,
        final EthPeers ethPeers,
        final EngineCallListener engineCallListener);
  }

  private final MethodFactory methodFactory;
  protected AbstractEngineNewPayload method;

  public AbstractEngineNewPayloadTest(final MethodFactory methodFactory) {
    this.methodFactory = methodFactory;
  }

  private static final Vertx vertx = Vertx.vertx();
  private static final Hash mockHash = Hash.hash(Bytes32.fromHexStringLenient("0x1337deadbeef"));
  private static final Address depositContractAddress =
      Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");

  @Mock private ProtocolSpec protocolSpec;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Mock protected MergeMiningCoordinator mergeCoordinator;

  @Mock protected MutableBlockchain blockchain;

  @Mock private EthPeers ethPeers;

  @Mock protected EngineCallListener engineCallListener;

  @BeforeEach
  public void before() {
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(ethPeers.peerCount()).thenReturn(1);
    this.method =
        methodFactory.create(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
  }

  @Test
  public abstract void shouldReturnExpectedMethodName();

  @Test
  public void shouldReturnValid() {
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidOnBlockExecutionError() {
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult("error 42"), Optional.empty(), Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHash);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("error 42");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnAcceptedOnLatestValidAncestorEmpty() {
    BlockHeader mockHeader = createBlockHeader(Optional.empty(), Optional.empty());
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.empty());
    if (validateTerminalPoWBlock()) {
      when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(any(BlockHeader.class)))
          .thenReturn(true);
    }

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(ACCEPTED.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnSuccessOnAlreadyPresent() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    Block mockBlock =
        new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));

    when(blockchain.getBlockByHash(any())).thenReturn(Optional.of(mockBlock));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidWithLatestValidHashIsABadBlock() {
    BlockHeader mockHeader = createBlockHeader(Optional.empty(), Optional.empty());
    Hash latestValidHash = Hash.hash(Bytes32.fromHexStringLenient("0xcafebabe"));

    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(mergeCoordinator.isBadBlock(mockHeader.getHash())).thenReturn(true);
    when(mergeCoordinator.getLatestValidHashOfBadBlock(mockHeader.getHash()))
        .thenReturn(Optional.of(latestValidHash));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEqualTo(Optional.of(latestValidHash));
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldNotReturnInvalidOnStorageException() {
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.empty(), new StorageException("database bedlam")),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    fromErrorResp(resp);
    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, times(0)).addBadBlock(any(), any());
    // verify mainnetBlockValidator does not add to bad block manager
  }

  @Test
  public void shouldNotReturnInvalidOnHandledMerkleTrieException() {
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.empty(), new MerkleTrieException("missing leaf")),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, times(0)).addBadBlock(any(), any());

    fromErrorResp(resp);
  }

  @Test
  public void shouldNotReturnInvalidOnThrownMerkleTrieException() {
    BlockHeader mockHeader = createBlockHeader(Optional.empty(), Optional.empty());
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.of(mockHash));
    if (validateTerminalPoWBlock()) {
      when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(any(BlockHeader.class)))
          .thenReturn(true);
    }
    when(mergeCoordinator.rememberBlock(any())).thenThrow(new MerkleTrieException("missing leaf"));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, never()).addBadBlock(any(), any());

    fromErrorResp(resp);
  }

  @Test
  public void shouldReturnInvalidBlockHashOnBadHashParameter() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().buildHeader();

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(getExpectedInvalidBlockHashStatus().name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldCheckBlockValidityBeforeCheckingByHashForExisting() {
    BlockHeader realHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeader paramHeader = spy(realHeader);
    when(paramHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));

    var resp = resp(mockPayload(paramHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(getExpectedInvalidBlockHashStatus().name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidOnMalformedTransactions() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().buildHeader();
    when(mergeCoordinator.getLatestValidAncestor(any(Hash.class)))
        .thenReturn(Optional.of(mockHash));

    var resp = resp(mockPayload(mockHeader, List.of("0xDEAD", "0xBEEF")));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHash);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Failed to decode transactions from block parameter");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidOnOldTimestampInBlock() {
    BlockHeader parent = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parent.getHash())
            .timestamp(parent.getTimestamp())
            .buildHeader();
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(parent.getHash())).thenReturn(Optional.of(parent));
    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    var res = ((JsonRpcSuccessResponse) resp).getResult();
    assertThat(res).isInstanceOf(EnginePayloadStatusResult.class);
    var payloadStatusResult = (EnginePayloadStatusResult) res;
    assertThat(payloadStatusResult.getStatus()).isEqualTo(INVALID);
    assertThat(payloadStatusResult.getError()).isEqualTo("block timestamp not greater than parent");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldRespondWithSyncingDuringForwardSync() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    when(mergeContext.isSyncing()).thenReturn(Boolean.TRUE);
    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getError()).isNull();
    assertThat(res.getStatusAsString()).isEqualTo(SYNCING.name());
    assertThat(res.getLatestValidHash()).isEmpty();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldRespondWithSyncingDuringBackwardsSync() {
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    when(mergeCoordinator.appendNewPayloadToSync(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(SYNCING.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  @Disabled
  public void shouldRespondWithInvalidTerminalPowBlock() {
    // TODO: implement this as part of https://github.com/hyperledger/besu/issues/3141
    // mergeContext is a mock
    //    assertThat(mergeContext.getTerminalTotalDifficulty()).isNull();
  }

  @Test
  public void shouldRespondWithInvalidIfExtraDataIsNull() {
    BlockHeader realHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeader paramHeader = spy(realHeader);
    when(paramHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));
    when(paramHeader.getExtraData().toHexString()).thenReturn(null);

    var resp = resp(mockPayload(paramHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Field extraData must not be null");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidWhenBadBlock() {
    when(mergeCoordinator.isBadBlock(any(Hash.class))).thenReturn(true);
    BlockHeader mockHeader = new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).contains(Hash.ZERO);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Block already present in bad block manager.");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNotNull_WhenWithdrawalsProhibited() {
    final List<WithdrawalParameter> withdrawals = List.of();
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.of(Collections.emptyList()), Optional.empty()),
                Collections.emptyList(),
                withdrawals,
                null));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfWithdrawalsIsNull_WhenWithdrawalsProhibited() {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), withdrawals, null));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNull_WhenWithdrawalsAllowed() {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                withdrawals,
                null));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfWithdrawalsIsNotNull_WhenWithdrawalsAllowed() {
    final List<WithdrawalParameter> withdrawalsParam = List.of(WITHDRAWAL_PARAM_1);
    final List<Withdrawal> withdrawals = List.of(WITHDRAWAL_PARAM_1.toWithdrawal());
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.of(withdrawals),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), withdrawalsParam, null));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNotNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = List.of();
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.empty(), Optional.of(Collections.emptyList())),
                Collections.emptyList(),
                null,
                deposits));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfDepositsIsNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = null;
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), null, deposits));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNull_WhenDepositsAllowed() {
    final List<DepositParameter> deposits = null;
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));

    var resp =
        resp(
            mockPayload(
                createBlockHeader(Optional.empty(), Optional.empty()),
                Collections.emptyList(),
                null,
                deposits));

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfDepositsIsNotNull_WhenDepositsAllowed() {
    final List<DepositParameter> depositsParam = List.of(DEPOSIT_PARAM_1);
    final List<Deposit> deposits = List.of(DEPOSIT_PARAM_1.toDeposit());
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.of(deposits));

    var resp = resp(mockPayload(mockHeader, Collections.emptyList(), null, depositsParam));

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnValidIfProtocolScheduleIsEmpty() {
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(null);
    BlockHeader mockHeader =
        setupValidPayload(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());

    var resp = resp(mockPayload(mockHeader, Collections.emptyList()));

    assertValidResponse(mockHeader, resp);
  }

  protected JsonRpcResponse resp(final EnginePayloadParameter payload) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", RpcMethod.ENGINE_NEW_PAYLOAD_V2.getMethodName(), new Object[] {payload})));
  }

  protected EnginePayloadParameter mockPayload(final BlockHeader header, final List<String> txs) {
    return new EnginePayloadParameter(
        header.getHash(),
        header.getParentHash(),
        header.getCoinbase(),
        header.getStateRoot(),
        new UnsignedLongParameter(header.getNumber()),
        header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
        new UnsignedLongParameter(header.getGasLimit()),
        new UnsignedLongParameter(header.getGasUsed()),
        new UnsignedLongParameter(header.getTimestamp()),
        header.getExtraData() == null ? null : header.getExtraData().toHexString(),
        header.getReceiptsRoot(),
        header.getLogsBloom(),
        header.getPrevRandao().map(Bytes32::toHexString).orElse("0x0"),
        txs,
        null,
        null);
  }

  private EnginePayloadParameter mockPayload(
      final BlockHeader header,
      final List<String> txs,
      final List<WithdrawalParameter> withdrawals,
      final List<DepositParameter> deposits) {
    return new EnginePayloadParameter(
        header.getHash(),
        header.getParentHash(),
        header.getCoinbase(),
        header.getStateRoot(),
        new UnsignedLongParameter(header.getNumber()),
        header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
        new UnsignedLongParameter(header.getGasLimit()),
        new UnsignedLongParameter(header.getGasUsed()),
        new UnsignedLongParameter(header.getTimestamp()),
        header.getExtraData() == null ? null : header.getExtraData().toHexString(),
        header.getReceiptsRoot(),
        header.getLogsBloom(),
        header.getPrevRandao().map(Bytes32::toHexString).orElse("0x0"),
        txs,
        withdrawals,
        deposits);
  }

  @NotNull
  private BlockHeader setupValidPayload(
      final BlockProcessingResult value,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits) {
    BlockHeader mockHeader = createBlockHeader(maybeWithdrawals, maybeDeposits);
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.of(mockHash));
    if (validateTerminalPoWBlock()) {
      when(mergeCoordinator.latestValidAncestorDescendsFromTerminal(any(BlockHeader.class)))
          .thenReturn(true);
    }
    when(mergeCoordinator.rememberBlock(any())).thenReturn(value);
    return mockHeader;
  }

  protected boolean validateTerminalPoWBlock() {
    return false;
  }

  protected ExecutionEngineJsonRpcMethod.EngineStatus getExpectedInvalidBlockHashStatus() {
    return INVALID;
  }

  protected EnginePayloadStatusResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EnginePayloadStatusResult.class::cast)
        .get();
  }

  private JsonRpcError fromErrorResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    return Optional.of(resp)
        .map(JsonRpcErrorResponse.class::cast)
        .map(JsonRpcErrorResponse::getError)
        .get();
  }

  protected BlockHeader createBlockHeader(
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits) {
    BlockHeader parentBlockHeader =
        new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 1)
            .withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null))
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
            .buildHeader();
    return mockHeader;
  }

  private void assertValidResponse(final BlockHeader mockHeader, final JsonRpcResponse resp) {
    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHeader.getHash());
    assertThat(res.getStatusAsString()).isEqualTo(VALID.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }
}
