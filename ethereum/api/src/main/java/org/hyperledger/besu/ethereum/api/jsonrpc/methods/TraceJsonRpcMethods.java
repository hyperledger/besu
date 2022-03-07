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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceCall;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceCallMany;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceGet;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceReplayBlockTransactions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

import java.util.Map;

public class TraceJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private final PrivacyParameters privacyParameters;

  TraceJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final PrivacyParameters privacyParameters) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.privacyParameters = privacyParameters;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.TRACE.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final BlockReplay blockReplay =
        new BlockReplay(
            protocolSchedule,
            blockchainQueries.getBlockchain(),
            blockchainQueries.getWorldStateArchive());
    return mapOf(
        new TraceReplayBlockTransactions(
            () -> new BlockTracer(blockReplay), protocolSchedule, blockchainQueries),
        new TraceFilter(() -> new BlockTracer(blockReplay), protocolSchedule, blockchainQueries),
        new TraceGet(() -> new BlockTracer(blockReplay), blockchainQueries, protocolSchedule),
        new TraceTransaction(
            () -> new BlockTracer(blockReplay), protocolSchedule, blockchainQueries),
        new TraceBlock(() -> new BlockTracer(blockReplay), protocolSchedule, blockchainQueries),
        new TraceCall(
            blockchainQueries,
            protocolSchedule,
            new TransactionSimulator(
                blockchainQueries.getBlockchain(),
                blockchainQueries.getWorldStateArchive(),
                protocolSchedule,
                privacyParameters)),
        new TraceCallMany(
            blockchainQueries,
            protocolSchedule,
            new TransactionSimulator(
                blockchainQueries.getBlockchain(),
                blockchainQueries.getWorldStateArchive(),
                protocolSchedule,
                privacyParameters)),
        new TraceRawTransaction(
            protocolSchedule,
            blockchainQueries,
            new TransactionSimulator(
                blockchainQueries.getBlockchain(),
                blockchainQueries.getWorldStateArchive(),
                protocolSchedule,
                privacyParameters)));
  }
}
