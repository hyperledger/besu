/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.crypto;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;

public class SignatureAlgorithmType {

  public static final String DEFAULT_EC_CURVE_NAME = "secp256k1";
  private static final ImmutableMap<String, Supplier<SignatureAlgorithm>> SUPPORTED_ALGORITHMS =
      ImmutableMap.of(DEFAULT_EC_CURVE_NAME, SECP256K1::new, "secp256r1", SECP256R1::new);

  public static final Supplier<SignatureAlgorithm> DEFAULT_SIGNATURE_ALGORITHM_TYPE =
      SUPPORTED_ALGORITHMS.get(DEFAULT_EC_CURVE_NAME);

  private final Supplier<SignatureAlgorithm> instantiator;

  private SignatureAlgorithmType(final Supplier<SignatureAlgorithm> instantiator) {
    this.instantiator = instantiator;
  }

  public static SignatureAlgorithmType create(final String ecCurve)
      throws IllegalArgumentException {
    if (!isValidType(ecCurve)) {
      throw new IllegalArgumentException(invalidTypeErrorMessage(ecCurve));
    }

    return new SignatureAlgorithmType(SUPPORTED_ALGORITHMS.get(ecCurve));
  }

  public static SignatureAlgorithmType createDefault() {
    return new SignatureAlgorithmType(DEFAULT_SIGNATURE_ALGORITHM_TYPE);
  }

  public SignatureAlgorithm getInstance() {
    return instantiator.get();
  }

  public static boolean isValidType(final String ecCurve) {
    return SUPPORTED_ALGORITHMS.containsKey(ecCurve);
  }

  public static boolean isDefault(final SignatureAlgorithm signatureAlgorithm) {
    return signatureAlgorithm.getCurveName().equals(DEFAULT_EC_CURVE_NAME);
  }

  private static String invalidTypeErrorMessage(final String invalidEcCurve) {
    return new StringBuilder()
        .append(invalidEcCurve)
        .append(" is not in the list of valid elliptic curves ")
        .append(getEcCurvesListAsString())
        .toString();
  }

  private static String getEcCurvesListAsString() {
    Iterator<Map.Entry<String, Supplier<SignatureAlgorithm>>> it =
        SUPPORTED_ALGORITHMS.entrySet().iterator();

    StringBuilder ecCurveListBuilder = new StringBuilder();
    ecCurveListBuilder.append("[");

    while (it.hasNext()) {
      ecCurveListBuilder.append(it.next().getKey());

      if (it.hasNext()) {
        ecCurveListBuilder.append(", ");
      }
    }
    ecCurveListBuilder.append("]");

    return ecCurveListBuilder.toString();
  }
}
