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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt64;

/**
 * Helper for interacting with the Validator Withdrawal Request Contract
 * (https://eips.ethereum.org/EIPS/eip-7002)
 *
 * <p>TODO: Please note that this is not the spec-way of interacting with the Validator Withdrawal
 * Request contract. See https://github.com/hyperledger/besu/issues/6918 for more information.
 */
public class WithdrawalRequestContractHelper {

  public static final Address WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS =
      Address.fromHexString("0x00A3ca265EBcb825B45F985A16CEFB49958cE017");

  /** private constructor to prevent instantiations */
  private WithdrawalRequestContractHelper() {}

  @VisibleForTesting
  // Storage slot to store the difference between number of withdrawal requests since last block and
  // target withdrawal requests
  // per block
  static final UInt256 EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT = UInt256.valueOf(0L);

  @VisibleForTesting
  // Storage slot to store the number of withdrawal requests added since last block
  static final UInt256 WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT = UInt256.valueOf(1L);

  @VisibleForTesting
  static final UInt256 WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT = UInt256.valueOf(2L);

  @VisibleForTesting
  static final UInt256 WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT = UInt256.valueOf(3L);

  private static final UInt256 WITHDRAWAL_REQUEST_QUEUE_STORAGE_OFFSET = UInt256.valueOf(4L);

  // How many slots each withdrawal request occupies in the account state
  private static final int WITHDRAWAL_REQUEST_STORAGE_SLOT_SIZE = 3;

  public static final int MAX_WITHDRAWAL_REQUESTS_PER_BLOCK = 16;

  private static final int TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK = 2;

  private static final UInt256 INITIAL_EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT =
      UInt256.valueOf(1181);

  // TODO-lucas Add MIN_WITHDRAWAL_REQUEST_FEE and WITHDRAWAL_REQUEST_FEE_UPDATE_FRACTION

  /*
   Pop the expected list of withdrawal requests from the smart contract, updating the queue pointers and other
   control variables in the contract state.
  */
  public static List<WithdrawalRequest> popWithdrawalRequestsFromQueue(
      final MutableWorldState mutableWorldState) {
    final WorldUpdater worldUpdater = mutableWorldState.updater();
    final MutableAccount account = worldUpdater.getAccount(WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS);
    if (account == null || Hash.EMPTY.equals(account.getCodeHash())) {
      return List.of();
    }

    final List<WithdrawalRequest> withdrawalRequests = dequeueWithdrawalRequests(account);
    updateExcessWithdrawalRequests(account);
    resetWithdrawalRequestsCount(account);

    worldUpdater.commit();

    return withdrawalRequests;
  }

  private static List<WithdrawalRequest> dequeueWithdrawalRequests(final MutableAccount account) {
    final UInt256 queueHeadIndex =
        account.getStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT);
    final UInt256 queueTailIndex =
        account.getStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT);

    final List<WithdrawalRequest> withdrawalRequests =
        peekExpectedWithdrawalRequests(account, queueHeadIndex, queueTailIndex);

    final UInt256 newQueueHeadIndex = queueHeadIndex.plus(withdrawalRequests.size());
    if (newQueueHeadIndex.equals(queueTailIndex)) {
      // Queue is empty, reset queue pointers
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, UInt256.valueOf(0L));
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_TAIL_STORAGE_SLOT, UInt256.valueOf(0L));
    } else {
      account.setStorageValue(WITHDRAWAL_REQUEST_QUEUE_HEAD_STORAGE_SLOT, newQueueHeadIndex);
    }

    return withdrawalRequests;
  }

  /*
  ;; Each stack element has the following layout:
  ;;
  ;; A: addr
  ;;  0x00 | 00 00 00 00 00 00 00 00 00 00 00 00 aa aa aa aa
  ;;  0x10 | aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa
  ;;
  ;; B: pk[0:32]
  ;;  0x00 | bb bb bb bb bb bb bb bb bb bb bb bb bb bb bb bb
  ;;  0x10 | bb bb bb bb bb bb bb bb bb bb bb bb bb bb bb bb
  ;;
  ;; C: pk[32:48] ++ am[0:8] -> pk2_am
  ;;  0x00 | cc cc cc cc cc cc cc cc cc cc cc cc cc cc cc cc
  ;;  0x10 | dd dd dd dd dd dd dd dd 00 00 00 00 00 00 00 00
  ;;
  ;; To get these three stack elements into the correct contiguous format, it is
  ;; necessary to combine them in the follow form:
  ;;
  ;;  (A[12:32] ++ B[0:12], B[12:32] ++ C[0:12], C[12:24])
   */
  private static List<WithdrawalRequest> peekExpectedWithdrawalRequests(
      final Account account, final UInt256 queueHeadIndex, final UInt256 queueTailIndex) {
    final long numRequestsInQueue = queueTailIndex.subtract(queueHeadIndex).toLong();
    final long numRequestsDequeued =
        Long.min(numRequestsInQueue, MAX_WITHDRAWAL_REQUESTS_PER_BLOCK);

    final List<WithdrawalRequest> withdrawalRequests = new ArrayList<>();

    for (int i = 0; i < numRequestsDequeued; i++) {
      final UInt256 queueStorageSlot =
          WITHDRAWAL_REQUEST_QUEUE_STORAGE_OFFSET.plus(
              queueHeadIndex.plus(i).multiply(WITHDRAWAL_REQUEST_STORAGE_SLOT_SIZE));
      final Address sourceAddress =
          Address.wrap(account.getStorageValue(queueStorageSlot).toBytes().slice(12, 20));
      final BLSPublicKey validatorPublicKey =
          BLSPublicKey.wrap(
              Bytes.concatenate(
                  account
                      .getStorageValue(queueStorageSlot.plus(1))
                      .toBytes()
                      .slice(0, 32), // no need to slice
                  account.getStorageValue(queueStorageSlot.plus(2)).toBytes().slice(0, 16)));
      final UInt64 amount =
          UInt64.fromBytes(account.getStorageValue(queueStorageSlot.plus(2)).slice(16, 8));

      withdrawalRequests.add(
          new WithdrawalRequest(sourceAddress, validatorPublicKey, GWei.of(amount)));
    }

    return withdrawalRequests;
  }

  private static void updateExcessWithdrawalRequests(final MutableAccount account) {
    UInt256 previousExcessRequests =
        account.getStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT);

    if (previousExcessRequests.equals(INITIAL_EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT)) {
      previousExcessRequests = UInt256.ZERO;
    }

    final UInt256 requestsCount = account.getStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT);

    UInt256 newExcessRequests = UInt256.valueOf(0L);
    if (previousExcessRequests.plus(requestsCount).toLong()
        > TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK) {
      newExcessRequests =
          previousExcessRequests.plus(requestsCount).subtract(TARGET_WITHDRAWAL_REQUESTS_PER_BLOCK);
    }

    account.setStorageValue(EXCESS_WITHDRAWAL_REQUESTS_STORAGE_SLOT, newExcessRequests);
  }

  private static void resetWithdrawalRequestsCount(final MutableAccount account) {
    account.setStorageValue(WITHDRAWAL_REQUEST_COUNT_STORAGE_SLOT, UInt256.valueOf(0L));
  }
}
