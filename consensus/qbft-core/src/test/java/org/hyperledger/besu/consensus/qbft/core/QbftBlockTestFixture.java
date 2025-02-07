/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.validation.QbftBlockHeaderTestFixture;

public class QbftBlockTestFixture {

  private QbftBlockHeader blockHeader = new QbftBlockHeaderTestFixture().buildHeader();
  private boolean isEmpty = true;

  public QbftBlockTestFixture blockHeader(final QbftBlockHeader blockHeader) {
    this.blockHeader = blockHeader;
    return this;
  }

  public QbftBlockTestFixture isEmpty(final boolean isEmpty) {
    this.isEmpty = isEmpty;
    return this;
  }

  public QbftBlock build() {
    return new TestQbftBlock();
  }

  class TestQbftBlock implements QbftBlock {

    @Override
    public QbftBlockHeader getHeader() {
      return blockHeader;
    }

    @Override
    public boolean isEmpty() {
      return isEmpty;
    }
  }
}
