/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc;

import tech.pegasys.pantheon.ethereum.core.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.TraceReplayBlockTransactions;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockReplay;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockTracer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpBySpecTest {

  public TraceJsonRpcHttpBySpecTest(final String specFileName) {
    super(specFileName);
  }

  @Override
  public void setup() throws Exception {
    super.setup();
    startService();
  }

  @Override
  protected BlockchainSetupUtil<Void> getBlockchainSetupUtil() {
    return BlockchainSetupUtil.forMainnet();
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
        new TraceReplayBlockTransactions(new JsonRpcParameter(), blockTracer, blockchainQueries);
    methods.put(traceReplayBlockTransactions.getName(), traceReplayBlockTransactions);

    return methods;
  }

  /*
   Mapping between Json-RPC method class and its spec files

   Formatter will be turned on to make this easier to read (one spec per line)
   @formatter:off
  */
  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final Multimap<Class<? extends JsonRpcMethod>, String> specs = ArrayListMultimap.create();

    specs.put(
        TraceReplayBlockTransactions.class, "trace/trace_replayBlockTransactions_emptyResult");
    specs.put(TraceReplayBlockTransactions.class, "trace/trace_replayBlockTransactions_earliest");
    specs.put(TraceReplayBlockTransactions.class, "trace/trace_replayBlockTransactions_latest");
    specs.put(TraceReplayBlockTransactions.class, "trace/trace_replayBlockTransactions_pending");
    specs.put(
        TraceReplayBlockTransactions.class,
        "trace/trace_replayBlockTransactions_invalidTraceOptions");
    specs.put(
        TraceReplayBlockTransactions.class,
        "trace/trace_replayBlockTransactions_invalidBlockParam");

    return specs.values();
  }
}
