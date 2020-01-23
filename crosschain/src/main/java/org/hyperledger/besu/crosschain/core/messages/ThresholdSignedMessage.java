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
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The message format is:
 *
 * <p>Core Message: Message Type Coordination Blockchain Id Coordination Contract Address
 * Originating Blockchain Id Crosschain Transaction Id Message Digest of the part of the Crosschain
 * Transaction being processed. This includes any subordinate transactions or views below the
 * current transaction or view. Message specific field.
 *
 * <p>Signed Core Message: Core Message Key Version used to sign Signature of the RLP of the Core
 * Message.
 *
 * <p>Message Core Message Part of the Crosschain Transaction being processed
 *
 * <p>Signed Message Signed Core Message Part of the Crosschain Transaction being processed
 *
 * <p>The message specific field is: Start: Transaction Time-out Block Number Commit: Nothing
 * Ignore: Nothing Transaction Ready: Blockchain Id that Subordinate Transaction was executed on.
 * View Result: Blockchain Id that Subordinate View was executed on. Block number when view was
 * executed. RLP encoded result
 */
public interface ThresholdSignedMessage {
  Logger LOG = LogManager.getLogger();

  static ThresholdSignedMessage decodeEncodedMessage(final BytesValue encoded) {
    RLPInput in = RLP.input(encoded);
    in.enterList();
    long type = in.readLongScalar();
    ThresholdSignedMessageType messageType = ThresholdSignedMessageType.create((int) type);
    switch (messageType) {
      case CROSSCHAIN_TRANSACTION_START:
        return new CrosschainTransactionStartMessage(in);
      case CROSSCHAIN_TRANSACTION_COMMIT:
        return new CrosschainTransactionCommitMessage(in);
      case CROSSCHAIN_TRANSACTION_IGNORE:
        return new CrosschainTransactionIgnoreMessage(in);
      case SUBORDINATE_VIEW_RESULT:
        return new SubordinateViewResultMessage(in);
      case SUBORDINATE_TRANSACTION_READY:
        return new SubordinateTransactionReadyMessage(in);
      default:
        String msg = "Unknown Threshold Message type " + messageType;
        LOG.error(msg);
        throw new RuntimeException(msg);
    }
  }

  void setSignature(long keyVersion, BytesValue signature);

  ThresholdSignedMessageType getType();

  BigInteger getCoordinationBlockchainId();

  Address getCoordinationContractAddress();

  BigInteger getOriginatingBlockchainId();

  BigInteger getCrosschainTransactionId();

  BytesValue getCrosschainTransactionHash();

  long getKeyVersion();

  BytesValue getSignature();

  CrosschainTransaction getTransaction();

  // Create a message to be signed.
  BytesValue getEncodedCoreMessage();

  // Create the blob to be sent to other nodes so they have enough information to know whether they
  // should sign.
  BytesValue getEncodedMessage();
}
