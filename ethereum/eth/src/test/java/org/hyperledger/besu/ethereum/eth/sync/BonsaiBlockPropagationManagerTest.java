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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;

import org.junit.Before;
import org.junit.BeforeClass;

public class BonsaiBlockPropagationManagerTest extends AbstractBlockPropagationManagerTest {

  private static Blockchain fullBlockchain;

  @BeforeClass
  public static void setupSuite() {
    fullBlockchain = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI).importAllBlocks();
  }

  @Before
  public void setup() {
    setup(DataStorageFormat.BONSAI);
  }

  @Override
  public Blockchain getFullBlockchain() {
    return fullBlockchain;
  }
}
