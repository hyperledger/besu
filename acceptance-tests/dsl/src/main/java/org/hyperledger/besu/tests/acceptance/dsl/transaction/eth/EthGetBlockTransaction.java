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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.eth;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;

public class EthGetBlockTransaction implements Transaction<Block> {
  private final DefaultBlockParameter blockParameter;
  private final boolean fullTransactionObjects;

  EthGetBlockTransaction(
      final DefaultBlockParameter blockParameter, final boolean fullTransactionObjects) {
    this.blockParameter = blockParameter;
    this.fullTransactionObjects = fullTransactionObjects;
  }

  @Override
  public Block execute(final NodeRequests node) {
    try {
      final EthBlock result =
          node.eth().ethGetBlockByNumber(blockParameter, fullTransactionObjects).send();
      assertThat(result).isNotNull();
      assertThat(result.hasError()).isFalse();
      return result.getBlock();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
