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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.RewardTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceFilter extends TraceBlock {

  private static final Logger LOG = LoggerFactory.getLogger(TraceFilter.class);

  public TraceFilter(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries) {
    super(blockTracerSupplier, protocolSchedule, blockchainQueries);
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_FILTER.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final FilterParameter filterParameter =
        requestContext.getRequiredParameter(0, FilterParameter.class);

    final long fromBlock = resolveBlockNumber(filterParameter.getFromBlock());
    final long toBlock = resolveBlockNumber(filterParameter.getToBlock());
    LOG.trace("Received RPC rpcName={} fromBlock={} toBlock={}", getName(), fromBlock, toBlock);

    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNodeWrapper resultArrayNode =
        new ArrayNodeWrapper(
            mapper.createArrayNode(), filterParameter.getAfter(), filterParameter.getCount());
    long currentBlockNumber = fromBlock;
    while (currentBlockNumber <= toBlock && !resultArrayNode.isFull()) {
      Optional<Block> blockByNumber =
          blockchainQueriesSupplier.get().getBlockchain().getBlockByNumber(currentBlockNumber);
      blockByNumber.ifPresent(
          block -> resultArrayNode.addAll(traceBlock(block, Optional.of(filterParameter))));
      currentBlockNumber++;
    }
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), resultArrayNode.getArrayNode());
  }

  @Override
  protected void generateTracesFromTransactionTraceAndBlock(
      final Optional<FilterParameter> maybeFilterParameter,
      final List<TransactionTrace> transactionTraces,
      final Block block,
      final ArrayNodeWrapper arrayNode) {

    final Iterator<TransactionTrace> iterator = transactionTraces.iterator();
    while (!arrayNode.isFull() && iterator.hasNext()) {
      maybeFilterParameter.ifPresentOrElse(
          filterParameter -> {
            final List<Address> fromAddress = filterParameter.getFromAddress();
            final List<Address> toAddress = filterParameter.getToAddress();
            FlatTraceGenerator.generateFromTransactionTraceAndBlock(
                    protocolSchedule, iterator.next(), block)
                .map(FlatTrace.class::cast)
                .filter(
                    trace ->
                        fromAddress.isEmpty()
                            || Optional.ofNullable(trace.getAction().getFrom())
                                .map(Address::fromHexString)
                                .map(fromAddress::contains)
                                .orElse(false))
                .filter(
                    trace ->
                        toAddress.isEmpty()
                            || Optional.ofNullable(trace.getAction().getTo())
                                .map(Address::fromHexString)
                                .map(toAddress::contains)
                                .orElse(false))
                .forEachOrdered(arrayNode::addPOJO);
          },
          new Runnable() {
            @Override
            public void run() {
              LOG.debug("No filter found. Unable to create traces");
            }
          });
    }
  }

  @Override
  protected void generateRewardsFromBlock(
      final Optional<FilterParameter> maybeFilterParameter,
      final Block block,
      final ArrayNodeWrapper resultArrayNode) {
    maybeFilterParameter.ifPresent(
        filterParameter -> {
          final List<Address> fromAddress = filterParameter.getFromAddress();
          if (fromAddress.isEmpty()) {
            final List<Address> toAddress = filterParameter.getToAddress();
            RewardTraceGenerator.generateFromBlock(protocolSchedule, block)
                .map(FlatTrace.class::cast)
                .filter(trace -> trace.getBlockNumber() != 0)
                .filter(
                    trace ->
                        toAddress.isEmpty()
                            || Optional.ofNullable(trace.getAction().getAuthor())
                                .map(Address::fromHexString)
                                .map(toAddress::contains)
                                .orElse(false))
                .forEachOrdered(resultArrayNode::addPOJO);
          }
        });
  }

  private long resolveBlockNumber(final BlockParameter param) {
    if (param.getNumber().isPresent()) {
      return param.getNumber().get();
    } else if (param.isLatest()) {
      return blockchainQueriesSupplier.get().headBlockNumber();
    } else {
      throw new IllegalStateException("Unknown block parameter type.");
    }
  }
}
