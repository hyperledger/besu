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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceReplayBlockTransactions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;

import java.net.URL;
import java.util.Map;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpBySpecTest {

  public TraceJsonRpcHttpBySpecTest(final String specName, final URL specURL) {
    super(specName, specURL);
  }

  @Override
  public void setup() throws Exception {
    super.setup();
    startService();
  }

  @Override
  protected BlockchainSetupUtil<Void> getBlockchainSetupUtil() {
    return createBlockchainSetupUtil(
        "trace/chain-data/genesis.json", "trace/chain-data/blocks.bin");
  }

  @Override
  protected Map<String, JsonRpcMethod> getRpcMethods(
      final JsonRpcConfiguration config, final BlockchainSetupUtil<Void> blockchainSetupUtil) {
    final Map<String, JsonRpcMethod> methods = super.getRpcMethods(config, blockchainSetupUtil);

    // Add trace_replayBlockTransactions
    // TODO: Once this method is generally enabled, we won't need to add this here
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            blockchainSetupUtil.getBlockchain(), blockchainSetupUtil.getWorldArchive());
    final BlockReplay blockReplay =
        new BlockReplay(
            blockchainSetupUtil.getProtocolSchedule(),
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive());
    final BlockTracer blockTracer = new BlockTracer(blockReplay);
    final TraceReplayBlockTransactions traceReplayBlockTransactions =
        new TraceReplayBlockTransactions(
            new JsonRpcParameter(),
            blockTracer,
            blockchainQueries,
            blockchainSetupUtil.getProtocolSchedule());
    methods.put(traceReplayBlockTransactions.getName(), traceReplayBlockTransactions);

    return methods;
  }

  @Parameters(name = "{index}: {0}")
  public static Object[][] specs() {
    return AbstractJsonRpcHttpBySpecTest.findSpecFiles("trace");
  }
}
