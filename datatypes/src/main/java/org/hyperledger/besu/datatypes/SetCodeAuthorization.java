/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.datatypes;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;

/**
 * An access list entry as defined in EIP-7702
 *
 * @param chainId can be either the current chain id or zero
 * @param address the address from which the code will be set into the EOA account
 * @param nonces the list of nonces
 * @param signature the signature of the EOA account which will be used to set the code
 */
public record SetCodeAuthorization(
    BigInteger chainId, Address address, List<Long> nonces, SECPSignature signature) {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  /**
   * Create access list entry.
   *
   * @param chainId can be either the current chain id or zero
   * @param address the address from which the code will be set into the EOA account
   * @param nonces the list of nonces
   * @param v the recovery id
   * @param r the r value of the signature
   * @param s the s value of the signature
   * @return SetCodeTransactionEntry
   */
  @JsonCreator
  public static SetCodeAuthorization createAccessListEntry(
      @JsonProperty("chainId") final BigInteger chainId,
      @JsonProperty("address") final Address address,
      @JsonProperty("nonce") final List<Long> nonces,
      @JsonProperty("v") final byte v,
      @JsonProperty("r") final BigInteger r,
      @JsonProperty("s") final BigInteger s) {
    return new SetCodeAuthorization(
        chainId, address, nonces, SIGNATURE_ALGORITHM.get().createSignature(r, s, v));
  }
}
