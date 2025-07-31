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
package org.hyperledger.besu.ethereum.mainnet.block.access.list;

import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;

public class BlockAccessListFactory {

  private final boolean cliActivated;
  private final boolean forkActivated;

  public BlockAccessListFactory() {
    this(false, false);
  }

  public BlockAccessListFactory(final boolean cliActivated, final boolean forkActivated) {
    this.cliActivated = cliActivated;
    this.forkActivated = forkActivated;
  }

  public boolean isCliActivated() {
    return cliActivated;
  }

  public boolean isForkActivated() {
    return forkActivated;
  }

  public boolean isEnabled() {
    return cliActivated || forkActivated;
  }

  public BlockAccessListBuilder newBlockAccessListBuilder() {
    return BlockAccessList.builder();
  }

  public TransactionAccessList newTransactionAccessList(final int transactionIndex) {
    return new TransactionAccessList(transactionIndex);
  }
}
