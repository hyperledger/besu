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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction;

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;

public class GetAllPrivateMarkerTransactionHashes implements Transaction<List<String>> {
  @Override
  public List<String> execute(final NodeRequests node) {
    final Besu besu = node.privacy().getBesuClient();
    final List<String> toReturn = new ArrayList<>();
    try {
      final long blockchainHeight = besu.ethBlockNumber().send().getBlockNumber().longValueExact();
      for (long i = 0; i <= blockchainHeight; i++) {
        besu.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(i)), true)
            .send()
            .getBlock()
            .getTransactions()
            .forEach(
                t -> {
                  if (((EthBlock.TransactionObject) t).getTo().equals(DEFAULT_PRIVACY.toString())) {
                    toReturn.add(((EthBlock.TransactionObject) t).getHash());
                  }
                });
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return toReturn;
  }
}
