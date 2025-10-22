/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.CodeDelegation;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SetCodeAuthorizationParameter {

  private final String chainId;
  private final String address;
  private final String nonce;
  private final String yParity;
  private final String r;
  private final String s;

  @JsonCreator
  public SetCodeAuthorizationParameter(
      @JsonProperty("chainId") final String chainId,
      @JsonProperty("address") final String address,
      @JsonProperty("nonce") final String nonce,
      @JsonProperty("yParity") final String yParity,
      @JsonProperty("r") final String r,
      @JsonProperty("s") final String s) {
    this.chainId = chainId;
    this.address = address;
    this.nonce = nonce;
    this.yParity = yParity;
    this.r = r;
    this.s = s;
  }

  public CodeDelegation toAuthorization() {
    BigInteger rValue = new BigInteger(r.substring(2), 16);
    BigInteger sValue = new BigInteger(s.substring(2), 16);
    byte yParityByte = (byte) Integer.parseInt(yParity.substring(2), 16);

    // Convert yParity to v (27 or 28 for legacy, or 0/1 for EIP-155)
    BigInteger v = BigInteger.valueOf(yParityByte);

    SECPSignature signature = SECPSignature.create(rValue, sValue, yParityByte, v);

    return new org.hyperledger.besu.ethereum.core.CodeDelegation(
        new BigInteger(chainId.substring(2), 16),
        Address.fromHexString(address),
        Long.decode(nonce),
        signature);
  }

  // Getters...
}
