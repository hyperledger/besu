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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.ethereum.core.DepositContract;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.tx.Contract;

public class DepositLogDecoder {

  private static final LogTopic DEPOSIT_EVENT_TOPIC =
      LogTopic.wrap(
          Bytes.fromHexString(
              "0x649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c5"));

  public static Bytes decodeFromLog(final Log log) {
    // The deposit contract on Sepolia emits two events: Deposit and Transfer. We only are
    // interested in the Deposit event.
    if (log.getTopics().isEmpty() || !log.getTopics().getFirst().equals(DEPOSIT_EVENT_TOPIC)) {
      return Bytes.EMPTY;
    }

    Contract.EventValuesWithLog eventValues = DepositContract.staticExtractDepositEventWithLog(log);
    final Bytes rawPublicKey =
        Bytes.wrap((byte[]) eventValues.getNonIndexedValues().get(0).getValue());
    final Bytes rawWithdrawalCredential =
        Bytes.wrap((byte[]) eventValues.getNonIndexedValues().get(1).getValue());
    final Bytes rawAmount =
        Bytes.wrap((byte[]) eventValues.getNonIndexedValues().get(2).getValue());
    final Bytes rawSignature =
        Bytes.wrap((byte[]) eventValues.getNonIndexedValues().get(3).getValue());
    final Bytes rawIndex = Bytes.wrap((byte[]) eventValues.getNonIndexedValues().get(4).getValue());

    return Bytes.concatenate(
        rawPublicKey, rawWithdrawalCredential, rawAmount, rawSignature, rawIndex);
  }
}
