/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.core.messages;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

public abstract class AbstractThresholdSignedMessage implements ThresholdSignedMessage {
  protected long keyVersion = 0;
  protected BytesValue signature = null;
  protected CrosschainTransaction transaction;

  public AbstractThresholdSignedMessage() {}

  public AbstractThresholdSignedMessage(final CrosschainTransaction transaction) {
    this(transaction, 0, null);
  }

  public AbstractThresholdSignedMessage(
      final CrosschainTransaction transaction, final long keyVersion, final BytesValue signature) {
    this.transaction = transaction;
    this.keyVersion = keyVersion;
    this.signature = signature;
  }

  public AbstractThresholdSignedMessage(final RLPInput in) {
    decode(in);
  }

  @Override
  public void setSignature(final long keyVersion, final BytesValue sig) {
    this.keyVersion = keyVersion;
    this.signature = sig;
  }

  @Override
  public BigInteger getCoordinationBlockchainId() {
    return this.transaction.getCrosschainCoordinationBlockchainId().get();
  }

  @Override
  public Address getCoordinationContractAddress() {
    return this.transaction.getCrosschainCoordinationContractAddress().get();
  }

  @Override
  public BigInteger getOriginatingBlockchainId() {
    return this.transaction.getOriginatingSidechainId().orElse(this.transaction.getChainId().get());
  }

  @Override
  public BigInteger getCrosschainTransactionId() {
    return this.transaction.getCrosschainTransactionId().get();
  }

  @Override
  public BytesValue getCrosschainTransactionHash() {
    return this.transaction.hash();
  }

  @Override
  public long getKeyVersion() {
    return this.keyVersion;
  }

  @Override
  public BytesValue getSignature() {
    return this.signature;
  }

  @Override
  public CrosschainTransaction getTransaction() {
    return this.transaction;
  }

  @Override
  public BytesValue getEncodedCoreMessage() {
    throw new Error("not implemented yet");
  }

  @Override
  public BytesValue getEncodedMessage() {
    throw new Error("not implemented yet");
  }

  protected void sharedEncoding(final RLPOutput out) {
    out.writeLongScalar(getType().value);
    out.writeBigIntegerScalar(getCoordinationBlockchainId());
    out.writeBytesValue(getCoordinationContractAddress());
    out.writeBigIntegerScalar(getOriginatingBlockchainId());
    out.writeBigIntegerScalar(getCrosschainTransactionId());
    out.writeBytesValue(getCrosschainTransactionHash());
  }

  protected void decode(final RLPInput in) {
    throw new Error("not implemented yet");
  }
}
