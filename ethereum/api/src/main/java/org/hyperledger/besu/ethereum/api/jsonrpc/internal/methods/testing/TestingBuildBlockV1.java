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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.testing;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TestingBuildBlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobsBundleV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResultV4;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.blockcreation.GenericBlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockValueCalculator;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.HexUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class TestingBuildBlockV1 implements JsonRpcMethod {

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;
  private final TransactionPool transactionPool;
  private final EthScheduler ethScheduler;

  public TestingBuildBlockV1(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningConfiguration miningConfiguration,
      final TransactionPool transactionPool,
      final EthScheduler ethScheduler) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.transactionPool = transactionPool;
    this.ethScheduler = ethScheduler;
  }

  @Override
  public String getName() {
    return RpcMethod.TESTING_BUILD_BLOCK_V1.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final TestingBuildBlockParameter param;
    try {
      param = requestContext.getRequiredParameter(0, TestingBuildBlockParameter.class);
    } catch (final JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid testing_buildBlockV1 parameter (index 0)", RpcErrorType.INVALID_PARAMS, e);
    }

    final EnginePayloadAttributesParameter payloadAttributes = param.getPayloadAttributes();

    // Validate parent block exists
    final Optional<BlockHeader> maybeParentHeader =
        protocolContext.getBlockchain().getBlockHeader(param.getParentBlockHash());
    if (maybeParentHeader.isEmpty()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_BLOCK_HASH_PARAMS);
    }
    final BlockHeader parentHeader = maybeParentHeader.get();

    // Decode transactions from hex RLP
    final List<Transaction> transactions;
    try {
      transactions =
          param.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(bytes -> TransactionDecoder.decodeOpaqueBytes(bytes, EncodingContext.BLOCK_BODY))
              .collect(Collectors.toList());
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PARAMS);
    }

    // Convert withdrawals
    final Optional<List<Withdrawal>> withdrawals =
        Optional.ofNullable(payloadAttributes.getWithdrawals())
            .map(
                wps ->
                    wps.stream()
                        .map(WithdrawalParameter::toWithdrawal)
                        .collect(Collectors.toList()));

    // Extra data
    final Bytes extraData = param.getExtraData().map(Bytes::fromHexString).orElse(Bytes.EMPTY);

    // Create block creator
    final GenericBlockCreator blockCreator =
        new GenericBlockCreator(
            miningConfiguration,
            (__, ___) -> payloadAttributes.getSuggestedFeeRecipient(),
            (__) -> extraData,
            transactionPool,
            protocolContext,
            protocolSchedule,
            ethScheduler);

    // Build the block
    final BlockCreationResult result;
    try {
      result =
          blockCreator.createBlock(
              Optional.of(transactions),
              Optional.of(Collections.emptyList()),
              withdrawals,
              Optional.of(payloadAttributes.getPrevRandao()),
              Optional.ofNullable(payloadAttributes.getParentBeaconBlockRoot()),
              Optional.empty(),
              payloadAttributes.getTimestamp(),
              false,
              parentHeader);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INTERNAL_ERROR);
    }

    // Format response as EngineGetPayloadResultV4
    final var block = result.getBlock();
    final List<String> txsAsHex =
        block.getBody().getTransactions().stream()
            .map(tx -> TransactionEncoder.encodeOpaqueBytes(tx, EncodingContext.BLOCK_BODY))
            .map(b -> HexUtils.toFastHex(b, true))
            .collect(Collectors.toList());

    final BlockWithReceipts blockWithReceipts =
        new BlockWithReceipts(block, result.getTransactionSelectionResults().getReceipts());
    final Wei blockValue = BlockValueCalculator.calculateBlockValue(blockWithReceipts);

    final BlobsBundleV1 blobsBundle = new BlobsBundleV1(block.getBody().getTransactions());

    final EngineGetPayloadResultV4 response =
        new EngineGetPayloadResultV4(
            block.getHeader(),
            txsAsHex,
            block.getBody().getWithdrawals(),
            Optional.empty(),
            Quantity.create(blockValue),
            blobsBundle);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
