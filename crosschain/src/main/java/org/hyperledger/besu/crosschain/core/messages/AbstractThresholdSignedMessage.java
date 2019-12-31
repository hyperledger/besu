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

import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

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

  public void setSignature(final long keyVersion, final BytesValue sig) {
    this.keyVersion = keyVersion;
    this.signature = sig;
  }

  public CrosschainTransaction getTransaction() {
    return this.transaction;
  }

  public long getKeyVersion() {
    return this.keyVersion;
  }

  public BytesValue getSignature() {
    return this.signature;
  }

  @Override
  public BytesValue getEncodedMessage() {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeLongScalar(getType().value);
          out.writeBytesValue(RLP.encode(this.transaction::writeTo));
          out.writeLongScalar(this.keyVersion);
          out.writeBytesValue(this.signature != null ? this.signature : BytesValue.EMPTY);
          out.endList();
        });
  }

  private void decode(final RLPInput in) {
    this.transaction = CrosschainTransaction.readFrom(in);
    this.keyVersion = in.readLongScalar();
    BytesValue sig = in.readBytesValue();
    if (sig.isZero()) {
      this.signature = null;
    } else {
      this.signature = sig;
    }
  }
}
