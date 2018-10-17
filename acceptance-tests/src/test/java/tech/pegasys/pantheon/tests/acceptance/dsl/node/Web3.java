/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Web3Sha3;

public class Web3 {

  private final Web3j web3j;

  public Web3(final Web3j web3) {
    this.web3j = web3;
  }

  public String web3Sha3(final String input) throws IOException {
    final Web3Sha3 result = web3j.web3Sha3(input).send();
    assertThat(result).isNotNull();
    assertThat(result.hasError()).isFalse();
    return result.getResult();
  }
}
