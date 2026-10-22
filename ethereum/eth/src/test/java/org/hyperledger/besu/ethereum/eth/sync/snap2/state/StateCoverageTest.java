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
package org.hyperledger.besu.ethereum.eth.sync.snap2.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class StateCoverageTest {

  private static final Bytes32 ACCOUNT = Bytes32.fromHexString("0x" + "aa".repeat(32));

  @Test
  void keepsThePendingStorageOfAnAccountUntilItIsRemoved() {
    final StateCoverage coverage = new StateCoverage();
    final StorageTask task = new StorageTask(ACCOUNT, Bytes32.ZERO);

    coverage.addStorageTask(task);
    // found by a hash decoded from another message
    final Bytes message = Bytes.concatenate(Bytes.of(1), ACCOUNT);
    assertThat(coverage.storageTask(Bytes32.wrap(message, 1))).isSameAs(task);
    assertThat(coverage.pendingStorageCount()).isEqualTo(1);

    coverage.removeStorageTask(ACCOUNT);
    assertThat(coverage.storageTask(ACCOUNT)).isNull();
    assertThat(coverage.pendingStorageCount()).isZero();
  }
}
