/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.core;

import java.util.List;
import java.util.Objects;

public class BlockReceipts {
  private final List<TransactionReceipt> receipts;
  private final boolean receiptsRootProcessed;

  public BlockReceipts(
      final List<TransactionReceipt> receipts, final boolean receiptsRootProcessed) {
    this.receipts = receipts;
    this.receiptsRootProcessed = receiptsRootProcessed;
  }

  public List<TransactionReceipt> getReceipts() {
    return receipts;
  }

  public boolean isReceiptsRootProcessed() {
    return receiptsRootProcessed;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlockReceipts that = (BlockReceipts) o;
    return isReceiptsRootProcessed() == that.isReceiptsRootProcessed()
        && getReceipts().equals(that.getReceipts());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getReceipts(), isReceiptsRootProcessed());
  }
}
