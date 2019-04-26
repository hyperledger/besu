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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.JsonRequestFactories;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

public class EeaGetTransactionCountTransaction implements Transaction<BigInteger> {

  private final String accountAddress;
  private String privacyGroupId;

  public EeaGetTransactionCountTransaction(
      final String accountAddress, final String privacyGroupId) {
    this.accountAddress = accountAddress;
    this.privacyGroupId = privacyGroupId;
  }

  @Override
  public BigInteger execute(final JsonRequestFactories node) {
    try {
      EthGetTransactionCount result =
          node.eea().eeaGetTransactionCount(accountAddress, privacyGroupId).send();
      assertThat(result).isNotNull();
      return result.getTransactionCount();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
