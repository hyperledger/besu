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
package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

public class RlpProvider {

  private RlpProvider() {}

  public static TransactionHandler transaction() {
    return new TransactionHandler(
        RlpRegistry.getInstance().getTransactionDecoder(),
        RlpRegistry.getInstance().getTransactionEncoder());
  }

  public static TransactionHandler pooledTransaction() {
    return new TransactionHandler(
        RlpRegistry.getInstance().getPooledTransactionDecoder(),
        RlpRegistry.getInstance().getPooledTransactionEncoder());
  }

  public static class TransactionHandler implements TransactionEncoder, TransactionDecoder {
    private final TransactionDecoder decoder;
    private final TransactionEncoder encoder;

    public TransactionHandler(final TransactionDecoder decoder, final TransactionEncoder encoder) {
      this.decoder = decoder;
      this.encoder = encoder;
    }

    @Override
    public Transaction readFrom(final RLPInput rlpInput) {
      return decoder.readFrom(rlpInput);
    }

    @Override
    public Transaction readFrom(final Bytes bytes) {
      return decoder.readFrom(bytes);
    }

    @Override
    public Transaction decodeOpaqueBytes(final Bytes bytes) {
      return decoder.decodeOpaqueBytes(bytes);
    }

    @Override
    public void writeTo(final Transaction transaction, final RLPOutput output) {
      encoder.writeTo(transaction, output);
    }

    @Override
    public Bytes encodeOpaqueBytes(final Transaction transaction) {
      return encoder.encodeOpaqueBytes(transaction);
    }
  }
}
