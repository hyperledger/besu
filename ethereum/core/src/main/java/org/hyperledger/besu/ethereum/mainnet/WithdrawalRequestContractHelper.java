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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ValidatorExit;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Helper for interacting with the Validator Exit Contract (https://eips.ethereum.org/EIPS/eip-7002)
 *
 * <p>TODO: Please note that this is not the spec-way of interacting with the Validator Exit
 * contract. See https://github.com/hyperledger/besu/issues/6918 for more information.
 */
public class WithdrawalRequestContractHelper {

  public static final Address WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS =
      Address.fromHexString("0xEd8EA01d70Cb49726175BCf2778B9C982912e017");

  @VisibleForTesting
  // Storage slot to store the difference between number of exits since last block and target exits
  // per block
  static final UInt256 EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT = UInt256.valueOf(0L);

  @VisibleForTesting
  // Storage slot to store the number of exits added since last block
  static final UInt256 WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT = UInt256.valueOf(1L);

  @VisibleForTesting
  static final UInt256 WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT = UInt256.valueOf(2L);

  @VisibleForTesting
  static final UInt256 WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT = UInt256.valueOf(3L);

  private static final UInt256 WITHDRAWAL_REQUEST_QUEUE_STORAGE_OFFSET = UInt256.valueOf(4L);

  // How many slots each exit occupies in the account state
  private static final int WITHDRAWAL_REQUEST_STORAGE_SLOT_SIZE = 3;

  @VisibleForTesting static final int MAX_WITHDRAWAL_REQUESTS_PER_BLOCK = 16;

  private static final int TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK = 2;

  //TODO-lucas Add MIN_WITHDRAWAL_REQUEST_FEE and WITHDRAWAL_REQUEST_FEE_UPDATE_FRACTION

  /*
   Pop the expected list of exits from the validator exit smart contract, updating the queue pointers and other
   control variables in the contract state.
  */
  public static List<ValidatorExit> popExitsFromQueue(final MutableWorldState mutableWorldState) {
    final WorldUpdater worldUpdater = mutableWorldState.updater();
    final MutableAccount account = worldUpdater.getAccount(WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS);
    if (Hash.EMPTY.equals(account.getCodeHash())) {
      return List.of();
    }

    final List<ValidatorExit> exits = dequeueExits(account);
    updateExcessExits(account);
    resetExitCount(account);

    worldUpdater.commit();

    return exits;
  }

  private static List<ValidatorExit> dequeueExits(final MutableAccount account) {
    final UInt256 queueHeadIndex = account.getStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT);
    final UInt256 queueTailIndex = account.getStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT);

    final List<ValidatorExit> exits = peekExpectedExits(account, queueHeadIndex, queueTailIndex);

    final UInt256 newQueueHeadIndex = queueHeadIndex.plus(exits.size());
    if (newQueueHeadIndex.equals(queueTailIndex)) {
      // Queue is empty, reset queue pointers
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, UInt256.valueOf(0L));
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT, UInt256.valueOf(0L));
    } else {
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, newQueueHeadIndex);
    }

    return exits;
  }

  private static List<ValidatorExit> peekExpectedExits(
      final Account account, final UInt256 queueHeadIndex, final UInt256 queueTailIndex) {
    final long numExitsInQueue = queueTailIndex.subtract(queueHeadIndex).toLong();
    final long numExitsDequeued = Long.min(numExitsInQueue, MAX_WITHDRAWAL_REQUESTS_PER_BLOCK);

    final List<ValidatorExit> exits = new ArrayList<>();

    for (int i = 0; i < numExitsDequeued; i++) {
      final UInt256 queueStorageSlot =
          WITHDRAWAL_REQUEST_QUEUE_STORAGE_OFFSET.plus(
              queueHeadIndex.plus(i).multiply(WITHDRAWAL_REQUEST_STORAGE_SLOT_SIZE));
      final Address sourceAddress =
          Address.wrap(account.getStorageValue(queueStorageSlot).toBytes().slice(12, 20));
      final BLSPublicKey validatorPubKey =
          BLSPublicKey.wrap(
              Bytes.concatenate(
                  account
                      .getStorageValue(queueStorageSlot.plus(1))
                      .toBytes()
                      .slice(0, 32), // no need to slice
                  account.getStorageValue(queueStorageSlot.plus(2)).toBytes().slice(0, 16)));

      exits.add(new ValidatorExit(sourceAddress, validatorPubKey));
    }

    return exits;
  }

  private static void updateExcessExits(final MutableAccount account) {
    final UInt256 previousExcessExits = account.getStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT);
    final UInt256 exitCount = account.getStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT);

    UInt256 newExcessExits = UInt256.valueOf(0L);
    if (previousExcessExits.plus(exitCount).toLong() > TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK) {
      newExcessExits = previousExcessExits.plus(exitCount).subtract(TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK);
    }

    account.setStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT, newExcessExits);
  }

  private static void resetExitCount(final MutableAccount account) {
    account.setStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, UInt256.valueOf(0L));
  }
}
