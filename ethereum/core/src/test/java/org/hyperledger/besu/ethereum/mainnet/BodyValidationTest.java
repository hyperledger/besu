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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests for {@link BodyValidation}. */
public final class BodyValidationTest {

  @Test
  public void calculateTransactionsRoot() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Hash transactionRoot = BodyValidation.transactionsRoot(body.getTransactions());
      Assertions.assertThat(transactionRoot).isEqualTo(header.getTransactionsRoot());
    }
  }

  @Test
  public void calculateOmmersHash() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Hash ommersHash = BodyValidation.ommersHash(body.getOmmers());
      Assertions.assertThat(header.getOmmersHash()).isEqualTo(ommersHash);
    }
  }

  @Test
  public void calculateWithdrawalsRoot() throws IOException {
    for (final int block : Arrays.asList(4156, 12691)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Hash withdrawalsRoot = BodyValidation.withdrawalsRoot(body.getWithdrawals().get());
      Assertions.assertThat(header.getWithdrawalsRoot()).hasValue(withdrawalsRoot);
    }
  }

  @Test
  public void calculateRequestsHash() {
    List<Request> requests =
        List.of(
            new Request(
                RequestType.DEPOSIT,
                Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100405973070000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000")),
            new Request(
                RequestType.WITHDRAWAL,
                Bytes.fromHexString(
                    "0x6389e7f33ce3b1e94e4325ef02829cd12297ef710000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000")),
            new Request(
                RequestType.CONSOLIDATION,
                Bytes.fromHexString(
                    "0x8a0a19589531694250d570040a0c4b74576919b8000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001")));

    Hash requestHash = BodyValidation.requestsHash(requests);
    Assertions.assertThat(requestHash.getBytes())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x0e53a6857da18cf29c6ae28be10a333fc0eaafbd3f425f09e5e81f29e4d3d766"));
  }

  @Test
  public void blockAccessListValidator_shouldPassWithinLimit() {
    // Create a BAL with 2 addresses and 3 storage keys (5 items total)
    final Address addr1 = Address.fromHexString("0x1000000000000000000000000000000000000001");
    final Address addr2 = Address.fromHexString("0x2000000000000000000000000000000000000002");

    final BlockAccessList.AccountChanges account1 =
        new BlockAccessList.AccountChanges(
            addr1,
            List.of(
                new BlockAccessList.SlotChanges(
                    new StorageSlotKey(UInt256.ONE),
                    List.of(new BlockAccessList.StorageChange(1, UInt256.valueOf(100))))),
            List.of(
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(2))),
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(3)))),
            List.of(),
            List.of(),
            List.of());

    final BlockAccessList.AccountChanges account2 =
        new BlockAccessList.AccountChanges(
            addr2, List.of(), List.of(), List.of(), List.of(), List.of());

    final BlockAccessList bal = new BlockAccessList(List.of(account1, account2));
    final BlockAccessListValidator validator = createValidatorWithAmsterdamGasCalculator();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .gasLimit(30_000_000L)
            .balHash(BodyValidation.balHash(bal))
            .buildHeader();

    // bal_items = 5, max_items = 15000 → should pass
    Assertions.assertThat(validator.validate(Optional.of(bal), header)).isTrue();
  }

  @Test
  public void blockAccessListValidator_shouldFailWhenExceedingLimit() {
    final Address addr1 = Address.fromHexString("0x1000000000000000000000000000000000000001");

    final BlockAccessList.AccountChanges account1 =
        new BlockAccessList.AccountChanges(
            addr1,
            List.of(
                new BlockAccessList.SlotChanges(
                    new StorageSlotKey(UInt256.ONE),
                    List.of(new BlockAccessList.StorageChange(1, UInt256.valueOf(100))))),
            List.of(
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(2))),
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(3))),
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(4))),
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(5))),
                new BlockAccessList.SlotRead(new StorageSlotKey(UInt256.valueOf(6)))),
            List.of(),
            List.of(),
            List.of());

    final BlockAccessList bal = new BlockAccessList(List.of(account1));
    final BlockAccessListValidator validator = createValidatorWithAmsterdamGasCalculator();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .gasLimit(10_000L)
            .balHash(BodyValidation.balHash(bal))
            .buildHeader();

    // bal_items = 7, max_items = 5 → should fail
    Assertions.assertThat(validator.validate(Optional.of(bal), header)).isFalse();
  }

  @Test
  public void blockAccessListValidator_shouldPassAtExactLimit() {
    final Address addr1 = Address.fromHexString("0x1000000000000000000000000000000000000001");
    final Address addr2 = Address.fromHexString("0x2000000000000000000000000000000000000002");

    final BlockAccessList.AccountChanges account1 =
        new BlockAccessList.AccountChanges(
            addr1,
            List.of(
                new BlockAccessList.SlotChanges(
                    new StorageSlotKey(UInt256.ONE),
                    List.of(new BlockAccessList.StorageChange(1, UInt256.valueOf(100))))),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    final BlockAccessList.AccountChanges account2 =
        new BlockAccessList.AccountChanges(
            addr2, List.of(), List.of(), List.of(), List.of(), List.of());

    final BlockAccessList bal = new BlockAccessList(List.of(account1, account2));
    final BlockAccessListValidator validator = createValidatorWithAmsterdamGasCalculator();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .gasLimit(6_000L)
            .balHash(BodyValidation.balHash(bal))
            .buildHeader();

    // bal_items = 3, max_items = 3 → should pass
    Assertions.assertThat(validator.validate(Optional.of(bal), header)).isTrue();
  }

  @Test
  public void blockAccessListValidator_shouldPassWithEmptyBAL() {
    final BlockAccessList bal = new BlockAccessList(List.of());
    final BlockAccessListValidator validator = createValidatorWithAmsterdamGasCalculator();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .gasLimit(30_000_000L)
            .balHash(BodyValidation.balHash(bal))
            .buildHeader();

    Assertions.assertThat(validator.validate(Optional.of(bal), header)).isTrue();
  }

  private static BlockAccessListValidator createValidatorWithAmsterdamGasCalculator() {
    final ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
    final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    when(protocolSpec.getGasCalculator())
        .thenReturn(new org.hyperledger.besu.evm.gascalculator.AmsterdamGasCalculator());
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    return new MainnetBlockAccessListValidator(protocolSchedule);
  }
}
