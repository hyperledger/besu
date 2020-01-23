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

/**
 * Holds a Subordinate View Transaction, Result, and block number the result relates to. The
 * information may be accompanied by a signature. The signature may be a full signature or a part
 * signature.
 */
public class SubordinateViewResultMessage extends AbstractThresholdSignedMessage {
  private BytesValue result;
  private long blockNumber;

  public SubordinateViewResultMessage(
      final CrosschainTransaction transaction, final BytesValue result, final long blockNumber) {
    super(transaction);
    this.result = result;
    this.blockNumber = blockNumber;
  }

  public SubordinateViewResultMessage(
      final CrosschainTransaction transaction,
      final BytesValue result,
      final long blockNumber,
      final long keyVersion,
      final BytesValue signature) {
    super(transaction, keyVersion, signature);
    this.result = result;
    this.blockNumber = blockNumber;
  }

  public SubordinateViewResultMessage(final RLPInput in) {
    decode(in);
  }

  @Override
  public ThresholdSignedMessageType getType() {
    return ThresholdSignedMessageType.SUBORDINATE_VIEW_RESULT;
  }

  public BytesValue getResult() {
    return this.result;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  @Override
  public BytesValue getEncodedMessage() {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeLongScalar(ThresholdSignedMessageType.SUBORDINATE_VIEW_RESULT.value);
          out.writeLongScalar(this.blockNumber);
          out.writeBytesValue(this.result);
          out.writeBytesValue(RLP.encode(this.transaction::writeTo));
          out.writeLongScalar(this.keyVersion);
          out.writeBytesValue(this.signature != null ? this.signature : BytesValue.EMPTY);
          out.endList();
        });
  }

  @Override
  protected void decode(final RLPInput in) {
    this.blockNumber = in.readLongScalar();
    this.result = in.readBytesValue();
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
