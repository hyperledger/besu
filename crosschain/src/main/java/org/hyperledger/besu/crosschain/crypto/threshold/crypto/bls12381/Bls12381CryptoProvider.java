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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto.bls12381;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.CryptoProviderBase;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;
import org.apache.milagro.amcl.BLS381.ROM;

public class Bls12381CryptoProvider extends CryptoProviderBase implements BlsCryptoProvider {
  private static final String SECURITY_DOMAIN = "BLS12";

  public BlsCryptoProvider.DigestAlgorithm digestAlgorithm;

  public Bls12381CryptoProvider(final BlsCryptoProvider.DigestAlgorithm alg) {
    this.digestAlgorithm = alg;
  }

  @Override
  public BigInteger modPrime(final BigInteger val) {
    BIG q = new BIG(ROM.Modulus);
    DBIG dval = Bls12381Util.DBIGFromBigInteger(val);
    BIG modval = dval.mod(q);
    BigInteger biRet = Bls12381Util.BigIntegerFromBIG(modval);
    return (biRet);
  }

  @Override
  public BigInteger getPrimeModulus() {
    BIG q = new BIG(ROM.Modulus);
    BigInteger biRet = Bls12381Util.BigIntegerFromBIG(q);
    return (biRet);
  }

  @Override
  public BlsPoint createPointE1(final BigInteger scalar) {
    org.apache.milagro.amcl.BLS381.ECP basedPoint = org.apache.milagro.amcl.BLS381.ECP.generator();
    BIG scBIG = Bls12381Util.BIGFromBigInteger(scalar);
    return new Bls12381PointWrapper(basedPoint.mul(scBIG));
  }

  @Override
  public BlsPoint getBasePointE1() {
    org.apache.milagro.amcl.BLS381.ECP basedPoint = ECP.generator();
    return new Bls12381PointWrapper(basedPoint);
  }

  @Override
  public BlsPoint hashToCurveE1(final byte[] data) {
    BytesValue dataBV1 = BytesValue.wrap(createSecuerityDomainPrefix(SECURITY_DOMAIN));
    BytesValue dataBV2 = BytesValue.wrap(data);
    BytesValue dataBV = BytesValues.concatenate(dataBV1, dataBV2);
    BlsPoint p = null;

    switch (this.digestAlgorithm) {
      case KECCAK256:
        Bytes32 digestedData = Hash.keccak256(dataBV);
        p = mapToCurveE1(digestedData.extractArray());
        break;
      default:
        throw new Error("not implemented yet!" + this.digestAlgorithm);
    }

    return p;
  }

  private static final BigInteger MAX_LOOP_COUNT = BigInteger.TEN;

  // TODO Review this https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04
  // TODO to work out whether this is a better approach.

  /**
   * Map a byte array to a point on the curve by converting the byte array to an integer and then
   * scalar multiplying the base point by the integer.
   */
  private BlsPoint mapToCurveE1(final byte[] data) {
    BigInteger q = getPrimeModulus();

    BigInteger ctr = BigInteger.ZERO;

    BlsPoint p = null;

    while (true) {
      byte[] c = ctr.toByteArray();

      /* Concatenate data with counter */
      byte[] dc = new byte[data.length + c.length];
      System.arraycopy(data, 0, dc, 0, data.length);
      System.arraycopy(c, 0, dc, data.length, c.length);

      // Convert back to a Big Integer mod q.
      // Indicate dc must be positive.
      BigInteger x = new BigInteger(1, dc);
      x = x.mod(q);

      p = createPointE1(x); // map to point

      // if map is valid, we are done
      if (!p.isAtInfinity()) {
        break;
      }

      // bump counter for next round, if necessary
      ctr = ctr.add(BigInteger.ONE);
      if (ctr.equals(MAX_LOOP_COUNT)) {
        throw new Error("Failed to map to point");
      }
    }

    return (p);
  }

  @Override
  public BlsPoint createPointE2(final BigInteger scalar) {
    ECP2 basedPoint = ECP2.generator();
    BIG bigScalar = Bls12381Util.BIGFromBigInteger(scalar);
    return new Bls12381Fq2PointWrapper(basedPoint.mul(bigScalar));
  }

  @Override
  public BlsPoint getBasePointE2() {
    ECP2 basedPoint = ECP2.generator();
    return new Bls12381Fq2PointWrapper(basedPoint);
  }

  @Override
  public BlsPoint hashToCurveE2(final byte[] data) {
    BytesValue dataBV1 = BytesValue.wrap(createSecuerityDomainPrefix(SECURITY_DOMAIN));
    BytesValue dataBV2 = BytesValue.wrap(data);
    BytesValue dataBV = BytesValues.concatenate(dataBV1, dataBV2);
    BlsPoint p = null;

    switch (this.digestAlgorithm) {
      case KECCAK256:
        Bytes32 digestedData = Hash.keccak256(dataBV);
        p = mapToCurveE2(digestedData.extractArray());
        break;
      default:
        throw new Error("not implemented yet!" + this.digestAlgorithm);
    }

    return p;
  }

  // TODO Review this https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04
  // TODO to work out whether this is a better approach.

  /**
   * Map a byte array to a point on the curve by converting the byte array to an integer and then
   * scalar multiplying the base point by the integer.
   */
  private BlsPoint mapToCurveE2(final byte[] data) {
    BigInteger q = getPrimeModulus();

    BigInteger ctr = BigInteger.ZERO;

    BlsPoint p = null;

    while (true) {
      byte[] c = ctr.toByteArray();

      /* Concatenate data with counter */
      byte[] dc = new byte[data.length + c.length];
      System.arraycopy(data, 0, dc, 0, data.length);
      System.arraycopy(c, 0, dc, data.length, c.length);

      // Convert back to a Big Integer mod q.
      // Indicate dc must be positive.
      BigInteger x = new BigInteger(1, dc);
      x = x.mod(q);

      p = createPointE2(x); // map to point

      // if map is valid, we are done
      if (!p.isAtInfinity()) {
        break;
      }

      // bump counter for next round, if necessary
      ctr = ctr.add(BigInteger.ONE);
      if (ctr.equals(MAX_LOOP_COUNT)) {
        throw new Error("Failed to map to point");
      }
    }

    return (p);
  }

  /**
   * Create a signature as a point on the E1 curve.
   *
   * @param privateKey Private key to sign data with.
   * @param data Data to be signed.
   * @return signature.
   */
  @Override
  public BlsPoint sign(final BigInteger privateKey, final byte[] data) {
    BlsPoint hashOfData = hashToCurveE1(data);
    return hashOfData.scalarMul(privateKey);
  }

  /**
   * Verify a signature.
   *
   * @param publicKey Point on the E2 curve to verify the data with.
   * @param data Data to be verified.
   * @param signature Signature on E1 curve.
   * @return true if the signature is verified.
   */
  @Override
  public boolean verify(final BlsPoint publicKey, final byte[] data, final BlsPoint signature) {
    BlsPoint hashOfData = hashToCurveE1(data);
    BlsPoint basePointE2 = getBasePointE2();

    ECP2 pk = ((Bls12381Fq2PointWrapper) publicKey).point;
    ECP hm = ((Bls12381PointWrapper) hashOfData).point;

    ECP2 g = ((Bls12381Fq2PointWrapper) basePointE2).point;
    ECP d = ((Bls12381PointWrapper) signature).point;
    d.neg();

    FP12 res1 = PAIR.ate2(g, d, pk, hm);
    FP12 v = PAIR.fexp(res1);

    boolean result = v.isunity();
    return result;
  }

  public boolean pair(final ECP p1, final ECP2 p2) {

    // TODO this is written as if in a loop, as in the code from the Pantheon precompile

    FP12 pairResult = PAIR.ate(p2, p1);

    if (pairResult.isunity()) {
      return true;
    }
    return false;
  }
}
