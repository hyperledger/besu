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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.besu.crypto.crosschain.BlsThresholdCryptoSystem;
import org.web3j.protocol.besu.response.crosschain.LongResponse;

public class CrossStartThresholdKeyGeneration implements Transaction<BigInteger> {
  private final int threshold;
  private final BlsThresholdCryptoSystem algorithm;

  CrossStartThresholdKeyGeneration(final int threshold, final BlsThresholdCryptoSystem algorithm) {
    this.threshold = threshold;
    this.algorithm = algorithm;
  }

  @Override
  public BigInteger execute(final NodeRequests node) {
    try {
      final LongResponse result =
          node.eth().crossStartThresholdKeyGeneration(threshold, algorithm).send();
      assertThat(result).isNotNull();
      assertThat(result.hasError()).isFalse();
      return BigInteger.valueOf(result.getValue());

    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
