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

/** The Signature algorithm type. */
public class SignatureAlgorithmType {

  /** The constant DEFAULT_EC_CURVE_NAME. */
  public static final String DEFAULT_EC_CURVE_NAME = "secp256k1";

  private static final ImmutableMap<String, Supplier<SignatureAlgorithm>> SUPPORTED_ALGORITHMS =
      ImmutableMap.of(DEFAULT_EC_CURVE_NAME, SECP256K1::new, "secp256r1", SECP256R1::new);

  /** The constant DEFAULT_SIGNATURE_ALGORITHM_TYPE. */
  public static final Supplier<SignatureAlgorithm> DEFAULT_SIGNATURE_ALGORITHM_TYPE =
      SUPPORTED_ALGORITHMS.get(DEFAULT_EC_CURVE_NAME);

  private final Supplier<SignatureAlgorithm> instantiator;

  private SignatureAlgorithmType(final Supplier<SignatureAlgorithm> instantiator) {
    this.instantiator = instantiator;
  }

  /**
   * Create signature algorithm type.
   *
   * @param ecCurve the ec curve
   * @return the signature algorithm type
   * @throws IllegalArgumentException the illegal argument exception
   */
  public static SignatureAlgorithmType create(final String ecCurve)
      throws IllegalArgumentException {
    if (!isValidType(ecCurve)) {
      throw new IllegalArgumentException(invalidTypeErrorMessage(ecCurve));
    }

    return new SignatureAlgorithmType(SUPPORTED_ALGORITHMS.get(ecCurve));
  }

  /**
   * Create default signature algorithm type.
   *
   * @return the signature algorithm type
   */
  public static SignatureAlgorithmType createDefault() {
    return new SignatureAlgorithmType(DEFAULT_SIGNATURE_ALGORITHM_TYPE);
  }

  /**
   * Gets instance.
   *
   * @return the instance
   */
  public SignatureAlgorithm getInstance() {
    return instantiator.get();
  }

  /**
   * Is valid type boolean.
   *
   * @param ecCurve the ec curve
   * @return the boolean
   */
  public static boolean isValidType(final String ecCurve) {
    return SUPPORTED_ALGORITHMS.containsKey(ecCurve);
  }

  /**
   * Is default signature algorithm.
   *
   * @param signatureAlgorithm the signature algorithm
   * @return the boolean
   */
  public static boolean isDefault(final SignatureAlgorithm signatureAlgorithm) {
    return signatureAlgorithm.getCurveName().equals(DEFAULT_EC_CURVE_NAME);
  }

  private static String invalidTypeErrorMessage(final String invalidEcCurve) {
    return invalidEcCurve
        + " is not in the list of valid elliptic curves "
        + getEcCurvesListAsString();
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
