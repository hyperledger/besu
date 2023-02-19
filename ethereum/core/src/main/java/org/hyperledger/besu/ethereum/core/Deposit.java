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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.*;
import org.hyperledger.besu.ethereum.core.encoding.DepositDecoder;
import org.hyperledger.besu.ethereum.core.encoding.DepositEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.PublicKey;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class Deposit implements org.hyperledger.besu.plugin.data.Deposit {

  private final BLSPublicKey pubKey;
  private final WithdrawalCredential withdrawalCredentials;
  private final GWei amount;
  private final BLSSignature signature;
  private final UInt64 index;

  public Deposit(
      BLSPublicKey pubKey,
      WithdrawalCredential withdrawalCredentials,
      GWei amount,
      BLSSignature signature,
      UInt64 index) {
    this.pubKey = pubKey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
    this.index = index;
  }

  public static Deposit readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  public static Deposit readFrom(final RLPInput rlpInput) {
    return DepositDecoder.decode(rlpInput);
  }

  public void writeTo(final RLPOutput out) {
    DepositEncoder.encode(this, out);
  }

  @Override
  public GWei getAmount() {
    return amount;
  }

  @Override
  public PublicKey getPublicKey() {
    return null;
  }

  @Override
  public WithdrawalCredential getWithdrawalCredentials() {
    return withdrawalCredentials;
  }

  @Override
  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public UInt64 getIndex() {
    return index;
  }

  @Override
  public String toString() {
    return "Deposit{"
        + "pubKey="
        + pubKey
        + ", withdrawalCredentials="
        + withdrawalCredentials
        + ", amount="
        + amount
        + ", signature="
        + signature
        + ", index="
        + index
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Deposit that = (Deposit) o;
    return Objects.equals(pubKey, that.pubKey)
        && Objects.equals(withdrawalCredentials, that.withdrawalCredentials)
        && Objects.equals(amount, that.amount)
        && Objects.equals(signature, that.signature)
        && Objects.equals(index, that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubKey, withdrawalCredentials, amount, signature, index);
  }
}
