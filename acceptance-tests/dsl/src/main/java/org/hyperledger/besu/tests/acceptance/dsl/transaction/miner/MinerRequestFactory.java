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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.miner;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGasPrice;

public class MinerRequestFactory {

  private final Web3jService web3jService;

  public MinerRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  Request<?, org.web3j.protocol.core.methods.response.VoidResponse> minerStart() {
    return new Request<>(
        "miner_start",
        null,
        web3jService,
        org.web3j.protocol.core.methods.response.VoidResponse.class);
  }

  Request<?, org.web3j.protocol.core.methods.response.VoidResponse> minerStop() {
    return new Request<>(
        "miner_stop",
        null,
        web3jService,
        org.web3j.protocol.core.methods.response.VoidResponse.class);
  }

  Request<?, EthGasPrice> minerGetMinGasPrice() {
    return new Request<>("miner_getMinGasPrice", null, web3jService, EthGasPrice.class);
  }
}
