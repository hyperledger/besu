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
package org.hyperledger.besu.crypto.altbn128;

import java.math.BigInteger;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
@SuppressWarnings("rawtypes")
public interface FieldElement<T extends FieldElement> {

  BigInteger FIELD_MODULUS =
      new BigInteger(
          "21888242871839275222246405745257275088696311157297823662689037894645226208583");

  boolean isValid();

  boolean isZero();

  T add(T other);

  T subtract(T other);

  T multiply(int val);

  T multiply(T other);

  T negate();

  T divide(T other);

  T power(int n);

  T power(BigInteger n);
}
