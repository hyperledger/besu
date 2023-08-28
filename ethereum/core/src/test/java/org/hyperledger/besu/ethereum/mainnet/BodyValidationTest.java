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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.io.IOException;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Tests for {@link BodyValidation}. */
public final class BodyValidationTest {

  @Test
  public void calculateTransactionsRoot() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 transactionRoot = BodyValidation.transactionsRoot(body.getTransactions());
      Assertions.assertThat(transactionRoot).isEqualTo(header.getTransactionsRoot());
    }
  }

  @Test
  public void calculateOmmersHash() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 ommersHash = BodyValidation.ommersHash(body.getOmmers());
      Assertions.assertThat(header.getOmmersHash()).isEqualTo(ommersHash);
    }
  }

  @Test
  public void calculateWithdrawalsRoot() throws IOException {
    for (final int block : Arrays.asList(4156, 12691)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 withdrawalsRoot = BodyValidation.withdrawalsRoot(body.getWithdrawals().get());
      Assertions.assertThat(header.getWithdrawalsRoot()).hasValue(Hash.wrap(withdrawalsRoot));
    }
  }

  @Disabled // TODO: RLP encoding has changed, so testdata needs to be updated
  @Test
  public void calculateDepositsRoot() throws IOException {
    for (final int block : Arrays.asList(123, 124)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 depositsRoot = BodyValidation.depositsRoot(body.getDeposits().get());
      Assertions.assertThat(header.getDepositsRoot()).hasValue(Hash.wrap(depositsRoot));
    }
  }
}
