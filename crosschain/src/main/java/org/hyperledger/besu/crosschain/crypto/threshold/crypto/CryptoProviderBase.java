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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto;

import com.google.common.base.Charsets;

/** Base class of all crypto providers. */
public abstract class CryptoProviderBase implements BlsCryptoProvider {
  private static String IMPLEMENTATION_NAME = "THRES";
  private static String VERSION_STRING = "-v01";
  private static String ALGORITHM_BASE = "-a";
  private static int ALG_TYPE_FIXED_LENGTH = 5;

  /**
   * Create a security domain separation parameter. See
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04#page-7 section 2.2.5 for a
   * discussion of Security Domain Separation.
   *
   * @param algType A fixed length string indicating the algorithm.
   * @return a byte array reflecting the security domain.
   */
  protected byte[] createSecuerityDomainPrefix(final String algType) {
    if (algType.length() != ALG_TYPE_FIXED_LENGTH) {
      throw new Error("Invalid agorithm type string");
    }

    String securityDomainString = IMPLEMENTATION_NAME + VERSION_STRING + ALGORITHM_BASE + algType;
    return securityDomainString.getBytes(Charsets.UTF_8);
  }
}
