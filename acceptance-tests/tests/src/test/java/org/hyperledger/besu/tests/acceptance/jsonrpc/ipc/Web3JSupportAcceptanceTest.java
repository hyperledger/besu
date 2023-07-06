/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.jsonrpc.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.NetVersion;
import org.web3j.protocol.ipc.UnixIpcService;

public class Web3JSupportAcceptanceTest extends AcceptanceTestBase {

  private Node node;
  private Path socketPath;

  @Before
  public void setUp() throws Exception {
    socketPath = Files.createTempFile("besu-test-", ".ipc");
    node =
        besu.createNode(
            "node1",
            (configurationBuilder) ->
                configurationBuilder.jsonRpcIpcConfiguration(
                    new JsonRpcIpcConfiguration(
                        true, socketPath, Collections.singletonList(RpcApis.NET.name()))));
    cluster.start(node);
  }

  @Test
  public void netVersionCall() {
    final String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    assumeTrue(osName.contains("mac") || osName.contains("linux"));

    final Web3j web3 = Web3j.build(new UnixIpcService(socketPath.toString()));
    final Request<?, NetVersion> ethBlockNumberRequest = web3.netVersion();
    node.verify(
        node -> {
          try {
            assertThat(ethBlockNumberRequest.send().getNetVersion())
                .isEqualTo(String.valueOf(2018));
          } catch (IOException e) {
            fail("Web3J net_version call failed", e);
          }
        });
  }
}
