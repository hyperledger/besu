/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.transaction;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.pantheon.Pantheon;

public class GetAllPrivacyMarkerTransactionHashes implements Transaction<List<String>> {
  @Override
  public List<String> execute(final NodeRequests node) {
    final Pantheon pantheon = node.privacy().getPantheonClient();
    final List<String> toReturn = new ArrayList<>();
    try {
      final long blockchainHeight =
          pantheon.ethBlockNumber().send().getBlockNumber().longValueExact();
      for (int i = 0; i <= blockchainHeight; i++) {
        pantheon
            .ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(i)), true)
            .send()
            .getBlock()
            .getTransactions()
            .forEach(
                t -> {
                  if (((EthBlock.TransactionObject) t)
                      .getTo()
                      .equals(Address.DEFAULT_PRIVACY.toString())) {
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
