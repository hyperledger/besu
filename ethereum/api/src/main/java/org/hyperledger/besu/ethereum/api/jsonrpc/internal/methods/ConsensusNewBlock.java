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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;

import io.vertx.core.Vertx;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class ConsensusNewBlock extends SyncJsonRpcMethod {
  private static final List<BlockHeader> OMMERS_CONSTANT = Collections.emptyList();
  private static final Hash OMMERS_HASH_CONSTANT = BodyValidation.ommersHash(OMMERS_CONSTANT);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;

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
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    // todo: see if we really need this. It seems like we get the validation already in importBlock
    //    final Hash blockHash = requestContext.getRequiredParameter(0, Hash.class);
    final Hash parentHash = requestContext.getRequiredParameter(1, Hash.class);
    final Address miner = requestContext.getRequiredParameter(2, Address.class);
    final Hash stateRoot = requestContext.getRequiredParameter(3, Hash.class);
    final long number =
        requestContext.getRequiredParameter(4, UnsignedLongParameter.class).getValue();
    final long gasLimit =
        requestContext.getRequiredParameter(5, UnsignedLongParameter.class).getValue();
    final long gasUsed =
        requestContext.getRequiredParameter(6, UnsignedLongParameter.class).getValue();
    final long timestamp =
        requestContext.getRequiredParameter(7, UnsignedLongParameter.class).getValue();
    final Hash receiptsRoot = requestContext.getRequiredParameter(8, Hash.class);
    final LogsBloomFilter logsBloom =
        new LogsBloomFilter(requestContext.getRequiredParameter(9, Hash.class));
    final List<Transaction> transactions =
        Arrays.asList(requestContext.getRequiredParameter(10, Transaction[].class));

    final Block newBlock =
        new Block(
            new BlockHeader(
                parentHash,
                OMMERS_HASH_CONSTANT,
                miner,
                stateRoot,
                BodyValidation.transactionsRoot(transactions),
                receiptsRoot,
                logsBloom,
                Difficulty.ONE,
                number,
                gasLimit,
                gasUsed,
                timestamp,
                Bytes.EMPTY,
                null,
                Hash.ZERO,
                0,
                new MainnetBlockHeaderFunctions()),
            new BlockBody(transactions, OMMERS_CONSTANT));

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        protocolSchedule
            .getByBlockNumber(number)
            .getBlockImporter()
            .importBlock(protocolContext, newBlock, HeaderValidationMode.FULL));
  }
}
