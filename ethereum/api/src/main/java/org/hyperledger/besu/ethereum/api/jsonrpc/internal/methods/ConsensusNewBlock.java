/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ConsensusNewBlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class ConsensusNewBlock extends SyncJsonRpcMethod {
  private static final List<BlockHeader> OMMERS_CONSTANT = Collections.emptyList();
  private static final Hash OMMERS_HASH_CONSTANT = BodyValidation.ommersHash(OMMERS_CONSTANT);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private static final Logger LOG = LogManager.getLogger();
  private static final ObjectMapper om = new ObjectMapper();

  public ConsensusNewBlock(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    super(vertx);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public String getName() {
    return RpcMethod.CONSENSUS_NEW_BLOCK.getMethodName();
  }

  @Override
  @SuppressWarnings("CatchAndPrintStackTrace")
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ConsensusNewBlockParameter blockParam =
        requestContext.getRequiredParameter(0, ConsensusNewBlockParameter.class);

    try {
      LOG.trace("blockparam: " + om.writeValueAsString(blockParam));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.warn("failed to decode transactions from newBlock RPC");
      e.printStackTrace();
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final BlockHeader newBlockHeader =
        new BlockHeader(
            blockParam.getParentHash(),
            OMMERS_HASH_CONSTANT,
            blockParam.getMiner(),
            blockParam.getStateRoot(),
            BodyValidation.transactionsRoot(transactions),
            blockParam.getReceiptsRoot(),
            blockParam.getLogsBloom(),
            Difficulty.ONE,
            blockParam.getNumber(),
            blockParam.getGasLimit(),
            blockParam.getGasUsed(),
            blockParam.getTimestamp(),
            Bytes.EMPTY,
            null,
            Hash.ZERO,
            0,
            new MainnetBlockHeaderFunctions());

    LOG.trace("newBlockHeader " + newBlockHeader);

    boolean blkHashEq = newBlockHeader.getHash().equals(blockParam.getBlockHash());
    LOG.trace("blkHashEq " + blkHashEq);
    boolean importSuccess =
        protocolSchedule
            .getByBlockNumber(blockParam.getNumber())
            .getBlockImporter()
            .importBlock(
                protocolContext,
                new Block(newBlockHeader, new BlockBody(transactions, OMMERS_CONSTANT)),
                HeaderValidationMode.FULL);
    LOG.trace("importSuccess " + importSuccess);

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), ImmutableMap.of("valid", blkHashEq && importSuccess));
  }
}
