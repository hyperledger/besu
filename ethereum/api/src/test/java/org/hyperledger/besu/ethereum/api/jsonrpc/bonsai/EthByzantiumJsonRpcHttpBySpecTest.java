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
package org.hyperledger.besu.ethereum.api.jsonrpc.bonsai;

import org.hyperledger.besu.ethereum.api.jsonrpc.AbstractJsonRpcHttpBySpecTest;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;

import org.junit.jupiter.api.BeforeEach;

public class EthByzantiumJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpBySpecTest {

  @Override
  @BeforeEach
  public void setup() throws Exception {
    setupBonsaiBlockchain();
    startService();
  }

  @Override
  protected BlockchainSetupUtil getBlockchainSetupUtil(final DataStorageFormat dataStorageFormat) {
    return createBlockchainSetupUtil(
        "trace/chain-data/genesis.json", "trace/chain-data/blocks.bin", dataStorageFormat);
  }

  public static Object[][] specs() {
    return AbstractJsonRpcHttpBySpecTest.findSpecFiles(new String[] {"eth_latest"});
  }
}
