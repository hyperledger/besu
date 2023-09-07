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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExecutionEngineEip6110AcceptanceTest extends AbstractJsonRpcTest {
  private static final String GENESIS_FILE = "/jsonrpc/engine/eip6110/genesis.json";
  private static final String TEST_CASE_PATH = "/jsonrpc/engine/eip6110/test-cases/";

  private static JsonRpcTestsContext testsContext;

  public ExecutionEngineEip6110AcceptanceTest(final String ignored, final URI testCaseFileURI) {
    super(ignored, testsContext, testCaseFileURI);
  }

  @BeforeClass
  public static void init() throws IOException {
    testsContext = new JsonRpcTestsContext(GENESIS_FILE);
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> testCases() throws URISyntaxException {
    return testCases(TEST_CASE_PATH);
  }

  @AfterClass
  public static void tearDown() {
    testsContext.cluster.close();
  }
}
