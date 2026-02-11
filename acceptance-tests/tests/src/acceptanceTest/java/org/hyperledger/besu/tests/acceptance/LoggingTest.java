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
package org.hyperledger.besu.tests.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class LoggingTest extends AcceptanceTestBase {

  @Test
  public void testDefaultLoggingIsAtLeastInfo() throws IOException {
    final BesuNode node = besu.createArchiveNode("logger");
    cluster.startConsoleCapture();
    cluster.runNodeStart(node);

    node.verify(net.awaitPeerCount(0));

    assertThat(cluster.getConsoleContents()).contains("INFO");
  }
}
