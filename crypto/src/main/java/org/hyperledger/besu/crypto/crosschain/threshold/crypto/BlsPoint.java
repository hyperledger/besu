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
package org.hyperledger.besu.crypto.crosschain.threshold.crypto;



import org.hyperledger.besu.crypto.crosschain.threshold.crypto.altbn128.AltBn128Fq2PointWrapper;
import org.hyperledger.besu.crypto.crosschain.threshold.crypto.altbn128.AltBn128PointWrapper;

import java.math.BigInteger;

/**
 * BLS public key - either a share or the group public key.
 */
public interface BlsPoint {
    // Add a point to this point.
    BlsPoint add(BlsPoint other);

    // Multiple this point by a scalar.
    BlsPoint scalarMul(BigInteger scalar);


    // Return true if this point is the point at infinity.
    boolean isAtInfity();

    // Negation is needed so the point can be verified on blockchain.
    BlsPoint negate();

    // Store the point data.
    byte[] store();

    // Load the point base on data.
    static BlsPoint load(final byte[] data) {
        if (data.length == AltBn128PointWrapper.STORED_LEN) {
            return AltBn128PointWrapper.load(data);
        }
        if (data.length == AltBn128Fq2PointWrapper.STORED_LEN) {
            return AltBn128Fq2PointWrapper.load(data);
        }
        throw new Error("Not implemented yet");
    }
}
