/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.rlp;

import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class TransactionReceiptRlpEncoder {
  public Bytes encode(final List<TransactionReceipt> transactionReceipts) {
    return RLP.encode(
        (rlpOutput) -> {
          if (transactionReceipts.isEmpty()) {
            rlpOutput.writeEmptyList();
          } else {
            transactionReceipts.forEach(
                (tr) ->
                    TransactionReceiptEncoder.writeTo(
                        tr, rlpOutput, TransactionReceiptEncodingConfiguration.DEFAULT));
          }
        });
  }
}
