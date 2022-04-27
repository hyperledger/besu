/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

public class ListReceipts extends ArrayList<TransactionReceipt> {

  private final Optional<Bytes> rlp;
  private Optional<LogsBloomFilter> logsBloom = Optional.empty();
  private Optional<Bytes32> receiptRoot = Optional.empty();

  public ListReceipts(final int size, final Bytes rlp) {
    super(size);
    this.rlp = Optional.ofNullable(rlp);
  }

  public ListReceipts(@NotNull final Collection<? extends TransactionReceipt> c) {
    super(c);
    if (c instanceof ListReceipts) {
      this.rlp = ((ListReceipts) c).getRlp();
      this.receiptRoot = ((ListReceipts) c).getReceiptRoot();
      this.logsBloom = ((ListReceipts) c).getLogsBloom();
    } else {
      this.rlp = Optional.empty();
    }
  }

  public Optional<Bytes32> getReceiptRoot() {
    return receiptRoot;
  }

  public void setReceiptRoot(final Optional<Bytes32> receiptRoot) {
    this.receiptRoot = receiptRoot;
  }

  public Optional<LogsBloomFilter> getLogsBloom() {
    return logsBloom;
  }

  public void setLogsBloom(final Optional<LogsBloomFilter> logsBloom) {
    this.logsBloom = logsBloom;
  }

  public Optional<Bytes> getRlp() {
    return rlp;
  }
}
