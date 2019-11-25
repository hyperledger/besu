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
package org.hyperledger.besu.crosschain.core.keys;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

/**
 * Exposes just the Blockchain Public Key and related meta-data. This is all the information that
 * needs to be stored in the Crosschain Coordination Contract.
 */
public interface BlsThresholdPublicKey {
  BlsPoint getPublicKey();

  long getKeyVersion();

  BigInteger getBlockchainId();

  BlsThresholdCryptoSystem getAlgorithm();

  BytesValue getEncodedPublicKey();

  BlsThresholdPublicKey NONE =
      new BlsThresholdPublicKey() {
        @Override
        public BlsPoint getPublicKey() {
          return null;
        }

        @Override
        public long getKeyVersion() {
          return 0;
        }

        @Override
        public BigInteger getBlockchainId() {
          return null;
        }

        @Override
        public BlsThresholdCryptoSystem getAlgorithm() {
          return null;
        }

        @Override
        public BytesValue getEncodedPublicKey() {
          return BytesValue.EMPTY;
        }
      };
}
