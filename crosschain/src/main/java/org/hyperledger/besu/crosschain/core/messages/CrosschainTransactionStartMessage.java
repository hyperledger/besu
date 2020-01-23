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

import java.math.BigInteger;

public class CrosschainTransactionStartMessage extends AbstractThresholdSignedMessage {

  public CrosschainTransactionStartMessage(final CrosschainTransaction transaction) {
    super(transaction);
  }

  public CrosschainTransactionStartMessage(
      final CrosschainTransaction transaction, final long keyVersion, final BytesValue signature) {
    super(transaction, keyVersion, signature);
  }

  public CrosschainTransactionStartMessage(final RLPInput in) {
    super(in);
  }

  @Override
  public ThresholdSignedMessageType getType() {
    return ThresholdSignedMessageType.CROSSCHAIN_TRANSACTION_START;
  }

  public BigInteger getTransactionTimeoutBlockNumber() {
    return this.transaction.getCrosschainTransactionTimeoutBlockNumber().get();
  }

  @Override
  public BytesValue getEncodedCoreMessage() {
    return RLP.encode(
        out -> {
          out.startList();
          sharedEncoding(out);
          out.writeBigIntegerScalar(getTransactionTimeoutBlockNumber());
          out.endList();
        });
  }

  @Override
  public BytesValue getEncodedMessage() {
    return RLP.encode(
        out -> {
          out.startList();
          sharedEncoding(out);
          out.writeBigIntegerScalar(getTransactionTimeoutBlockNumber());
          out.writeBytesValue(RLP.encode(this.transaction::writeTo));
          out.writeLongScalar(this.keyVersion);
          out.writeBytesValue(this.signature != null ? this.signature : BytesValue.EMPTY);
          out.endList();
        });
  }

  @Override
  protected void decode(final RLPInput in) {
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
