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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve;
import org.hyperledger.besu.nativelib.secp256r1.LibSECP256R1;
import org.hyperledger.besu.nativelib.secp256r1.Signature;

import java.math.BigInteger;
import java.util.Optional;

public class SECP256R1 extends AbstractSECP256 {

  private static final Logger LOG = LogManager.getLogger();
  public static final String CURVE_NAME = "secp256r1";
  private final boolean useNative = true;
  private final LibSECP256R1 libSECP256R1 = new LibSECP256R1();

  public SECP256R1() {
    super(CURVE_NAME, SecP256R1Curve.q);
  }

  @Override
  public void enableNative() {
    LOG.warn("Native secp256r1 requested but not available");
  }

  @Override
  public String getCurveName() {
    return CURVE_NAME;
  }

  @Override
  public SECPSignature sign(final Bytes32 dataHash, final KeyPair keyPair) {
    if (useNative) {
      return signNative(dataHash, keyPair);
    } else {
      return super.sign(dataHash, keyPair);
    }
  }

  @Override
  public boolean verify(final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {
    if (useNative) {
      return libSECP256R1.verify(data.toArrayUnsafe(), signature.getR().toByteArray(), signature.getS().toByteArray(), pub.getEncoded());
    } else {
      return super.verify(data, signature, pub);
    }
  }

  @Override
  public Optional<SECPPublicKey> recoverPublicKeyFromSignature(
      final Bytes32 dataHash, final SECPSignature signature) {
    if (useNative) {
      return recoverFromSignatureNative(dataHash, signature);
    } else {
      return super.recoverPublicKeyFromSignature(dataHash, signature);
    }
  }

  private SECPSignature signNative(final Bytes32 dataHash, final KeyPair keyPair) {
    Signature nativeSignature = libSECP256R1.sign(dataHash.toArrayUnsafe(), keyPair.getPrivateKey().getEncoded(), keyPair.getPublicKey().getEncoded());

    return new SECPSignature(
        new BigInteger(nativeSignature.getR()),
        new BigInteger(nativeSignature.getS()),
        nativeSignature.getV()
    );
  }

  private Optional<SECPPublicKey> recoverFromSignatureNative(final Bytes32 dataHash, final SECPSignature signature) {
    byte[] recoveredKey;

    try {
      recoveredKey = libSECP256R1.keyRecovery(dataHash.toArrayUnsafe(), signature.getR().toByteArray(), signature.getS().toByteArray(), signature.getRecId());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }

    return Optional.of(SECPPublicKey.create(Bytes.of(recoveredKey), ALGORITHM));
  }
}
