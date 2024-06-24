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
package org.hyperledger.besu.tests.acceptance.ethereum;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SetCodeTransactionAcceptanceTest extends AcceptanceTestBase {
  private static final String GENESIS_FILE = "/dev/dev_prague.json";

  private final Account authorizer =
      accounts.createAccount(
          Address.fromHexStringStrict("8b45ea85863ea7b3cff15a6c08e58d0b969a5151"));
  private final Account transactionSponsor =
      accounts.createAccount(
          Address.fromHexStringStrict("1a3f5c0744c80ba7a9df07f88e456acf9fa327b8"));

  private BesuNode besuNode;
  private PragueAcceptanceTestService testService;

  @BeforeEach
  void setUp() throws IOException {
    besuNode = besu.createExecutionEngineGenesisNode("besuNode", GENESIS_FILE);
    cluster.start(besuNode);

    testService = new PragueAcceptanceTestService(besuNode, ethTransactions);
  }

  @Test
  public void shouldTransferAllEthOfAuthorizerToSponsor() throws IOException {

    // RLP encoded 7702 transaction
    //
    // The authorizer account has signed the 7702 authorization to uses the contract deployed in the
    // genesis block,
    // the transactionSponsor account has signed the transaction
    //
    // 0x04 || rlp([
    //   "0x4EF3",
    //   "0x",
    //   "0x3B9ACA00",
    //   "0x02540BE400",
    //   "0x0F4240",
    //   "0x8b45ea85863ea7b3cff15a6c08e58d0b969a5151",
    //   "0x",
    //   "0x085b3b00000000000000000000000000a05b21E5186Ce93d2a226722b85D6e550Ac7D6E3",
    //   [],
    //   [[
    //     "0x4EF3",
    //     "0x0000000000000000000000000000000000009999",
    //     ["0x"],
    //     "0x01",
    //     "0x2b0c650c3bf6701937f420a7406a3b3d72b272f9f885be4391e6347ba0e6621a",
    //     "0x162bced47ddc2165f6bf0744e8b893cbff22c52c3f26088b27b4eefa3f707fb5"
    //   ]],
    //   "0x",
    //   "0x7e496cc1739a0f66e0ce52047010573d1f2cc083d396b76400063bcbce978bdc",
    //   "0x31164145c74b3e03467259cac1d88c2f77c484759c4a4d8875aa7d6c04f5f613"
    // ])
    final String raw7702Transaction =
        "04f8f3824ef380843b9aca008502540be400830f4240948b45ea85863ea7b3cff15a6c08e58d0b969a515180a4085b3b00000000000000000000000000a05b21e5186ce93d2a226722b85d6e550ac7d6e3c0f85ff85d824ef3940000000000000000000000000000000000009999c18001a02b0c650c3bf6701937f420a7406a3b3d72b272f9f885be4391e6347ba0e6621aa0162bced47ddc2165f6bf0744e8b893cbff22c52c3f26088b27b4eefa3f707fb580a07e496cc1739a0f66e0ce52047010573d1f2cc083d396b76400063bcbce978bdca031164145c74b3e03467259cac1d88c2f77c484759c4a4d8875aa7d6c04f5f613";

    besuNode.execute(ethTransactions.sendRawTransaction(raw7702Transaction));
    testService.buildNewBlock();

    cluster.verify(authorizer.balanceEquals(0));

    cluster.verify(
        transactionSponsor.balanceEquals(Amount.wei(new BigInteger("180000000000000000000000"))));
  }
}
