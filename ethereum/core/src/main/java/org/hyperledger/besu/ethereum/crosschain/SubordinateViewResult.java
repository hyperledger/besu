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
package org.hyperledger.besu.ethereum.crosschain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

/**
 * Holds a Subordinate View Transaction, Result, and block number the result relates to.
 * The information may be accompanied by a signature. The signature may be a full signature
 * or a part signature.
 *
 */
public class SubordinateViewResult {
    private static final Logger LOG = LogManager.getLogger();

    private CrosschainTransaction transaction = null;
    private BytesValue result;
    private long blockNumber;
    private BytesValue signature = null;
    private BytesValue encoded = null;

    public SubordinateViewResult(final CrosschainTransaction transaction, final BytesValue result, final long blockNumber) {
        this.transaction = transaction;
        this.result = result;
        this.blockNumber = blockNumber;
    }

    public SubordinateViewResult(final CrosschainTransaction transaction, final BytesValue result, final long blockNumber, final BytesValue signature) {
        this(transaction, result, blockNumber);
        this.signature = signature;
    }

    public SubordinateViewResult(final BytesValue encoded) {
        this.encoded = encoded;
    }

    public void setSignature(final BytesValue sig) {
        this.signature = sig;
    }


    public CrosschainTransaction getTransaction() {
        if (this.transaction == null) {
            if (this.encoded == null) {
                LOG.error("Neither Transaction nor encoded form exist1");
                return null;
            }
            else {
                decode();
            }
        }
        return this.transaction;
    }

    public BytesValue getResult() {
        if (this.result == null) {
            if (this.encoded == null) {
                LOG.error("Neither Result nor encoded form exist1");
                return null;
            }
            else {
                decode();
            }
        }
        return this.result;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public BytesValue getSignature() {
        return this.signature;
    }

    public BytesValue getEncoded() {
        if (this.encoded == null) {
            if (this.transaction == null) {
                LOG.error("Neither Transaction nor encoded form exist2");
                return null;
            }
            else {
                encode();
            }
        }
        return encoded;
    }


    private void decode() {
        RLPInput in = RLP.input(this.encoded);
        in.enterList();
        this.blockNumber = in.readLongScalar();
        this.result = in.readBytesValue();
        this.transaction = CrosschainTransaction.readFrom(in);
        BytesValue sig = in.readBytesValue();
        if (sig.isZero()) {
            this.signature = null;
        }
        else {
            this.signature = sig;
        }
    }

    private void encode() {
        this.encoded = RLP.encode(
            out -> {
                out.startList();
                out.writeLongScalar(this.blockNumber);
                out.writeBytesValue(this.result);
                out.writeBytesValue(RLP.encode(this.transaction::writeTo));
                out.writeBytesValue(this.signature != null ? this.signature : BytesValue.EMPTY);
                out.endList();
            });
    }


}
