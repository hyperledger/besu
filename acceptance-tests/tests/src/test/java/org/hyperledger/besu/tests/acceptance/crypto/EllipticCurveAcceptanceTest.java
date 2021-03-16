/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.tests.acceptance.crypto;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.condition.process.ExitedWithCode;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.io.IOException;

import org.junit.Test;

public class EllipticCurveAcceptanceTest extends AcceptanceTestBase {

  protected static final String INVALID_GENESIS_FILE = "/crypto/invalid_ec_curve_genesis.json";

  private Node node;

  @Test
  public void nodeShouldNotStartWithInvalidEllipticCurveOption() throws IOException {
    node = besu.createCustomGenesisNode("node1", INVALID_GENESIS_FILE, true);
    node.verify(new ExitedWithCode(1));
  }
}
