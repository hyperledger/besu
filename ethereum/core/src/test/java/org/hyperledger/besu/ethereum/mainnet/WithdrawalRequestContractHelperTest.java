/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WithdrawalRequestContractHelperTest {

  private MutableWorldState worldState;
  private MutableAccount contract;

  @BeforeEach
  public void setUp() {
    worldState = createInMemoryWorldStateArchive().getMutable();
  }

  @Test
  public void popExitsFromQueue_ReadWithdrawalRequestsCorrectly() {
    final List<WithdrawalRequest> validatorExits =
        List.of(createExit(), createExit(), createExit());
    loadContractStorage(worldState, validatorExits);

    final List<WithdrawalRequest> poppedExits =
        WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(worldState);

    assertThat(poppedExits).isEqualTo(validatorExits);
  }

  @Test
  public void popExitsFromQueue_whenContractCodeIsEmpty_ReturnsEmptyListOfWithdrawalRequests() {
    // Create account with empty code
    final WorldUpdater updater = worldState.updater();
    updater.createAccount(WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS);
    updater.commit();

    assertThat(WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(worldState))
        .isEmpty();
  }

  @Test
  public void popExitsFromQueue_WhenMoreWithdrawalRequests_UpdatesQueuePointers() {
    // Loading contract with more than 16 exits
    final List<WithdrawalRequest> validatorExits =
        IntStream.range(0, 30).mapToObj(__ -> createExit()).collect(Collectors.toList());
    loadContractStorage(worldState, validatorExits);
    // After loading the contract, the exit count since last block should match the size of the list
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, validatorExits.size());

    final List<WithdrawalRequest> poppedExits =
        WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(worldState);
    assertThat(poppedExits).hasSize(16);

    // Check that queue pointers were updated successfully (head advanced to index 16)
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, 16);
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT, 30);

    // We had 30 exits in the queue, and target per block is 2, so we have 28 excess
    assertContractStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT, 28);

    // We always reset the exit count after processing the queue
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, 0);
  }

  @Test
  public void popExitsFromQueue_WhenNoMoreWithdrawalRequests_ZeroQueuePointers() {
    final List<WithdrawalRequest> validatorExits =
        List.of(createExit(), createExit(), createExit());
    loadContractStorage(worldState, validatorExits);
    // After loading the contract, the exit count since last block should match the size of the list
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, validatorExits.size());

    final List<WithdrawalRequest> poppedExits =
        WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(worldState);
    assertThat(poppedExits).hasSize(3);

    // Check that queue pointers were updated successfully (head and tail zero because queue is
    // empty)
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, 0);
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT, 0);

    // We had 3 exits in the queue, target per block is 2, so we have 1 excess
    assertContractStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT, 1);

    // We always reset the exit count after processing the queue
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, 0);
  }

  @Test
  public void popExitsFromQueue_WhenNoWithdrawalRequests_DoesNothing() {
    // Loading contract with 0 exits
    loadContractStorage(worldState, List.of());
    // After loading storage, we have the exit count as zero because no exits were aded
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, 0);

    final List<WithdrawalRequest> poppedExits =
        WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(worldState);
    assertThat(poppedExits).hasSize(0);

    // Check that queue pointers are correct (head and tail are zero)
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, 0);
    assertContractStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT, 0);

    // We had 0 exits in the queue, and target per block is 2, so we have 0 excess
    assertContractStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT, 0);

    // We always reset the exit count after processing the queue
    assertContractStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, 0);
  }

  private void assertContractStorageValue(final UInt256 slot, final int expectedValue) {
    assertContractStorageValue(slot, UInt256.valueOf(expectedValue));
  }

  private void assertContractStorageValue(final UInt256 slot, final UInt256 expectedValue) {
    assertThat(worldState.get(WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS).getStorageValue(slot))
        .isEqualTo(expectedValue);
  }

  private void loadContractStorage(
      final MutableWorldState worldState, final List<WithdrawalRequest> withdrawalRequests) {
    final WorldUpdater updater = worldState.updater();
    contract = updater.getOrCreate(WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS);

    contract.setCode(
        Bytes.fromHexString(
            "0x61013680600a5f395ff33373fffffffffffffffffffffffffffffffffffffffe146090573615156028575f545f5260205ff35b36603014156101325760115f54600182026001905f5b5f82111560595781019083028483029004916001019190603e565b90939004341061013257600154600101600155600354806003026004013381556001015f3581556001016020359055600101600355005b6003546002548082038060101160a4575060105b5f5b81811460ed5780604402838201600302600401805490600101805490600101549160601b8160a01c17835260601b8160a01c17826020015260601b906040015260010160a6565b910180921460fe5790600255610109565b90505f6002555f6003555b5f546001546002828201116101205750505f610126565b01600290035b5f555f6001556044025ff35b5f5ffd"));
    // excess requests
    contract.setStorageValue(UInt256.valueOf(0), UInt256.valueOf(0));
    // requests count
    contract.setStorageValue(UInt256.valueOf(1), UInt256.valueOf(withdrawalRequests.size()));
    // requests queue head pointer
    contract.setStorageValue(UInt256.valueOf(2), UInt256.valueOf(0));
    // requests queue tail pointer
    contract.setStorageValue(UInt256.valueOf(3), UInt256.valueOf(withdrawalRequests.size()));

    int offset = 4;
    for (int i = 0; i < withdrawalRequests.size(); i++) {
      final WithdrawalRequest request = withdrawalRequests.get(i);
      // source_account
      contract.setStorageValue(
          // set account to slot, with 12 bytes padding on the left
          UInt256.valueOf(offset++),
          UInt256.fromBytes(
              Bytes.concatenate(
                  Bytes.fromHexString("0x000000000000000000000000"), request.getSourceAddress())));
      // validator_pubkey
      contract.setStorageValue(
          UInt256.valueOf(offset++), UInt256.fromBytes(request.getValidatorPubKey().slice(0, 32)));
      contract.setStorageValue(
          // set public key to slot, with 16 bytes padding on the right
          UInt256.valueOf(offset++),
          UInt256.fromBytes(
              Bytes.concatenate(
                  request.getValidatorPubKey().slice(32, 16),
                  request.getAmount().toBytes(), // 8 bytes for amount
                  Bytes.fromHexString("0x0000000000000000"))));
    }
    updater.commit();
  }

  private WithdrawalRequest createExit() {
    return new WithdrawalRequest(
        Address.extract(Bytes32.random()), BLSPublicKey.wrap(Bytes48.random()), GWei.ONE);
  }
}
