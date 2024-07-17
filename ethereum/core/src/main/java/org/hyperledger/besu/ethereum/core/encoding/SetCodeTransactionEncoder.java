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

import static org.hyperledger.besu.ethereum.core.encoding.AccessListTransactionEncoder.writeAccessList;
import static org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.writeSignatureAndRecoveryId;

import org.hyperledger.besu.datatypes.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class SetCodeTransactionEncoder {

  private SetCodeTransactionEncoder() {
    // private constructor
  }

  public static void encodeSetCodeInner(
      final List<SetCodeAuthorization> payloads, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    payloads.forEach(payload -> encodeSingleSetCode(payload, rlpOutput));
    rlpOutput.endList();
  }

  public static void encodeSingleSetCodeWithoutSignature(
      final SetCodeAuthorization payload, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    encodeAuthorizationDetails(payload, rlpOutput);
    rlpOutput.endList();
  }

  public static void encodeSingleSetCode(
      final SetCodeAuthorization payload, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    encodeAuthorizationDetails(payload, rlpOutput);
    rlpOutput.writeIntScalar(payload.signature().getRecId());
    rlpOutput.writeBigIntegerScalar(payload.signature().getR());
    rlpOutput.writeBigIntegerScalar(payload.signature().getS());
    rlpOutput.endList();
  }

  private static void encodeAuthorizationDetails(
      final SetCodeAuthorization payload, final RLPOutput rlpOutput) {
    rlpOutput.writeBigIntegerScalar(payload.chainId());
    rlpOutput.writeBytes(payload.address().copy());
    rlpOutput.startList();
    payload.nonce().ifPresent(rlpOutput::writeLongScalar);
    rlpOutput.endList();
  }

  public static void encode(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeBigIntegerScalar(transaction.getChainId().orElseThrow());
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getMaxPriorityFeePerGas().orElseThrow());
    out.writeUInt256Scalar(transaction.getMaxFeePerGas().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeAccessList(out, transaction.getAccessList());
    encodeSetCodeInner(
        transaction
            .getAuthorizationList()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Developer error: the transaction should be guaranteed to have a set code payload here")),
        out);
    writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }
}
