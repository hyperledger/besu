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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

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

  public static final Bytes MAGIC = Bytes.fromHexString("05");

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
  public static SetCodeAuthorization createSetCodeAuthorizationEntry(
      @JsonProperty("chainId") final BigInteger chainId,
      @JsonProperty("address") final Address address,
      @JsonProperty("nonce") final List<Long> nonces,
      @JsonProperty("v") final byte v,
      @JsonProperty("r") final BigInteger r,
      @JsonProperty("s") final BigInteger s) {
    return new SetCodeAuthorization(
        chainId, address, nonces, SIGNATURE_ALGORITHM.get().createSignature(r, s, v));
  }

  /**
   * Create set code authorization with a builder.
   *
   * @return SetCodeAuthorization.Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for SetCodeAuthorization. */
  public static class Builder {
    private BigInteger chainId = BigInteger.ZERO;
    private Address address;
    private List<Long> nonces = List.of();
    private SECPSignature signature;

    /** Create a new builder. */
    protected Builder() {}

    /**
     * Set the optional chain id.
     *
     * @param chainId the chain id
     * @return this builder
     */
    public Builder chainId(final BigInteger chainId) {
      this.chainId = chainId;
      return this;
    }

    /**
     * Set the address of the authorized smart contract.
     *
     * @param address the address
     * @return this builder
     */
    public Builder address(final Address address) {
      this.address = address;
      return this;
    }

    /**
     * Set the list of optional nonces.
     *
     * @param nonces the list of nonces. Only the first nonce will be used.
     * @return this builder
     */
    public Builder nonces(final List<Long> nonces) {
      this.nonces = nonces;
      return this;
    }

    /**
     * Set the signature of the authorizer account.
     *
     * @param signature the signature
     * @return this builder
     */
    public Builder signature(final SECPSignature signature) {
      this.signature = signature;
      return this;
    }

    /**
     * Sign the authorization with the given key pair and return the authorization.
     *
     * @param keyPair the key pair
     * @return SetCodeAuthorization
     */
    public SetCodeAuthorization signAndBuild(final KeyPair keyPair) {
      final BytesValueRLPOutput output = new BytesValueRLPOutput();
      output.startList();
      output.writeBigIntegerScalar(chainId);
      output.writeBytes(address);
      output.startList();
      nonces.forEach(output::writeLongScalar);
      output.endList();
      output.endList();

      signature(
          SIGNATURE_ALGORITHM
              .get()
              .sign(Hash.hash(Bytes.concatenate(MAGIC, output.encoded())), keyPair));
      return build();
    }

    /**
     * Build the authorization.
     *
     * @return SetCodeAuthorization
     */
    public SetCodeAuthorization build() {
      if (address == null) {
        throw new IllegalStateException("Address must be set");
      }

      if (signature == null) {
        throw new IllegalStateException("Signature must be set");
      }

      return new SetCodeAuthorization(chainId, address, nonces, signature);
    }
  }
}
