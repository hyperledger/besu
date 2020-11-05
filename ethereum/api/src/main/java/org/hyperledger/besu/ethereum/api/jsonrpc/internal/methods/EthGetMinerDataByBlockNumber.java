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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.MinerDataResult;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.units.bigints.BaseUInt256Value;

public class EthGetMinerDataByBlockNumber extends AbstractBlockParameterMethod {
  private final ProtocolSchedule protocolSchedule;

  public EthGetMinerDataByBlockNumber(
      final BlockchainQueries blockchain, final ProtocolSchedule protocolSchedule) {
    super(blockchain);
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_MINER_DATA_BY_BLOCK_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    BlockWithMetadata<TransactionWithMetadata, Hash> block =
        getBlockchainQueries().blockByNumber(blockNumber).orElse(null);

    MinerDataResult minerDataResult = null;
    if (block != null) {
      if (!getBlockchainQueries()
          .getWorldStateArchive()
          .isWorldStateAvailable(block.getHeader().getStateRoot())) {
        return new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.WORLD_STATE_UNAVAILABLE);
      }

      final BlockHeader blockHeader = block.getHeader();
      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(blockHeader.getNumber());
      final Wei staticBlockReward = protocolSpec.getBlockReward();
      final Wei transactionFee =
          block.getTransactions().stream()
              .map(
                  t -> {
                    Transaction transaction = t.getTransaction();
                    Optional<TransactionReceiptWithMetadata> transactionReceiptWithMetadata =
                        getBlockchainQueries()
                            .transactionReceiptByTransactionHash(transaction.getHash());
                    Wei refundAmount =
                        Wei.of(
                                transactionReceiptWithMetadata
                                    .flatMap(tr -> tr.getReceipt().getGasRemaining())
                                    .orElse(0L))
                            .multiply(transaction.getGasPrice());
                    return t.getTransaction().getUpfrontCost().subtract(refundAmount);
                  })
              .reduce(Wei.ZERO, BaseUInt256Value::add);
      final Wei uncleInclusionReward =
          staticBlockReward.multiply(block.getOmmers().size()).divide(32);
      final Wei netBlockReward = staticBlockReward.add(transactionFee).add(uncleInclusionReward);
      final Map<Hash, Address> uncleRewards = new HashMap<>();
      blockchainQueries
          .get()
          .getBlockchain()
          .getBlockByNumber(blockNumber)
          .ifPresent(
              blockBody ->
                  blockBody
                      .getBody()
                      .getOmmers()
                      .forEach(header -> uncleRewards.put(header.getHash(), header.getCoinbase())));

      minerDataResult =
          new MinerDataResult(
              netBlockReward,
              staticBlockReward,
              transactionFee,
              uncleInclusionReward,
              uncleRewards,
              blockHeader.getCoinbase(),
              blockHeader.getExtraData(),
              blockHeader.getDifficulty(),
              block.getTotalDifficulty());
    }

    return minerDataResult;
  }
}
