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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.evm.log.Log;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class PrivateTransactionReceiptTestFixture {

  private int status = 1;
  private List<Log> logs = Collections.emptyList();
  private Bytes output = Bytes.EMPTY;
  private Bytes revertReason = null;

  public PrivateTransactionReceipt create() {
    return new PrivateTransactionReceipt(status, logs, output, Optional.ofNullable(revertReason));
  }

  public PrivateTransactionReceiptTestFixture status(final int status) {
    this.status = status;
    return this;
  }

  public PrivateTransactionReceiptTestFixture logs(final List<Log> logs) {
    this.logs = logs;
    return this;
  }

  public PrivateTransactionReceiptTestFixture output(final Bytes output) {
    this.output = output;
    return this;
  }

  public PrivateTransactionReceiptTestFixture revertReason(final Bytes revertReason) {
    this.revertReason = revertReason;
    return this;
  }
}
