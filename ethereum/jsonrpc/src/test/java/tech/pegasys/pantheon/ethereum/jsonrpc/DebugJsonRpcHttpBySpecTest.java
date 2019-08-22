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

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugAccountRange;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Collection;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpBySpecTest {

  public DebugJsonRpcHttpBySpecTest(final String specFileName) {
    super(specFileName);
  }

  @Override
  public void setup() throws Exception {
    super.setup();
    startService();
  }

  /*
   Mapping between Json-RPC method class and its spec files

   Formatter will be turned on to make this easier to read (one spec per line)
   @formatter:off
  */
  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final Multimap<Class<? extends JsonRpcMethod>, String> specs = ArrayListMultimap.create();

    specs.put(DebugAccountRange.class, "debug/debug_accountRange_blockHash");
    specs.put(DebugAccountRange.class, "debug/debug_accountRange_complete");
    specs.put(DebugAccountRange.class, "debug/debug_accountRange_partial");
    specs.put(DebugAccountRange.class, "debug/debug_storageRangeAt_blockHash");
    specs.put(DebugAccountRange.class, "debug/debug_storageRangeAt_blockNumber");
    specs.put(DebugAccountRange.class, "debug/debug_storageRangeAt_midBlock");

    return specs.values();
  }
}
