/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.encoding.WithdrawalDecoder;
import org.hyperledger.besu.ethereum.core.encoding.WithdrawalEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.datatypes.GWei;

import java.util.StringJoiner;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class Withdrawal {
  private final UInt64 index;
  private final UInt64 validatorIndex;
  private final Address address;
  private final GWei amount;

  public Withdrawal(
      final UInt64 index, final UInt64 validatorIndex, final Address address, final GWei amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public static Withdrawal readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  public static Withdrawal readFrom(final RLPInput rlpInput) {
    return WithdrawalDecoder.decode(rlpInput);
  }

  public UInt64 getIndex() {
    return index;
  }

  public Address getAddress() {
    return address;
  }

  public GWei getAmount() {
    return amount;
  }

  public UInt64 getValidatorIndex() {
    return validatorIndex;
  }

  public void writeTo(final RLPOutput out) {
    WithdrawalEncoder.encode(this, out);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", Withdrawal.class.getSimpleName() + "[", "]")
        .add("index=" + index)
        .add("validatorIndex=" + validatorIndex)
        .add("address=" + address)
        .add("amount=" + amount)
        .toString();
  }
}
