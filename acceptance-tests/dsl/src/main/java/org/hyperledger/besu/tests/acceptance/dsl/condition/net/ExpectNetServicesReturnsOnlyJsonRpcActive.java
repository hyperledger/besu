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
package org.hyperledger.besu.tests.acceptance.dsl.condition.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetServicesTransaction;
import org.hyperledger.besu.util.NetworkUtility;

import java.util.Collections;
import java.util.Map;

import com.google.common.net.InetAddresses;

public class ExpectNetServicesReturnsOnlyJsonRpcActive implements Condition {

  private final NetServicesTransaction transaction;
  private static final String JSON_RPC_CONSTANT = "jsonrpc";

  public ExpectNetServicesReturnsOnlyJsonRpcActive(final NetServicesTransaction transaction) {
    this.transaction = transaction;
  }

  @Override
  public void verify(final Node node) {
    final Map<String, Map<String, String>> result = node.execute(transaction);
    assertThat(result.keySet())
        .containsExactlyInAnyOrderElementsOf(Collections.singletonList(JSON_RPC_CONSTANT));

    assertThat(InetAddresses.isUriInetAddress(result.get(JSON_RPC_CONSTANT).get("host"))).isTrue();
    final int jsonrpcPort = Integer.parseInt(result.get(JSON_RPC_CONSTANT).get("port"));
    assertThat(NetworkUtility.isValidPort(jsonrpcPort)).isTrue();
  }
}
