/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.crypto.threshold.scheme;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Implementation of Shamir's Threshold Scheme.
 *
 * <p>The equation is: y = a*x^m-1 + b*x^m-2 + c*x^m-3 +.... d*x^2 + e*x + f mod p
 *
 * <p>The scheme works in the following way:
 *
 * <p>p is a large prime number, chosen to be larger than the secret being protected. m is the
 * threshold. m random coefficients are generated in the range 1 to p-1. n participants each have an
 * x value, where x is between 1 and p-1. y values for the n participants are calculated. The shares
 * are the y value for each x value. The shares are distributed to the participants. Any m
 * participants can combine their shares to re-create the secret, or any other y value for an x
 * value.
 *
 * <p>The system applies equally well to BLS Points.
 */
public final class ThresholdScheme {
  private BlsCryptoProvider cryptoProvider;

  // Large prime number which must be bigger than the secret being protected.
  private final BigInteger prime;

  // The threshold of the scheme. That is the M value of the M of N scheme.
  private final int threshold;

  // Random number generator used to generate random coefficients from.
  private final SecureRandom random;

  /**
   * This constructor can be used when the class is to be used for all operations except for
   * generating new coefficients.
   *
   * @param cryptoProvider provides cryptographic algorithm implementations.
   * @param threshold The number of nodes which must combine to produce the random result.
   */
  public ThresholdScheme(final BlsCryptoProvider cryptoProvider, final int threshold) {
    this(cryptoProvider, threshold, null);
  }

  /**
   * This constructor can be used when the class is to be used for all usages.
   *
   * @param cryptoProvider provides cryptographic algorithm implementations.
   * @param threshold The number of nodes which must combine to produce the random result.
   * @param rand The PRNG used for generating the random coefficients.
   */
  public ThresholdScheme(
      final BlsCryptoProvider cryptoProvider, final int threshold, final SecureRandom rand) {
    this.cryptoProvider = cryptoProvider;
    this.prime = this.cryptoProvider.getPrimeModulus();
    this.random = rand;
    this.threshold = threshold;
  }

  public BigInteger[] generateRandomCoefficients() {
    return generateRandomCoefficients(this.threshold);
  }

  /**
   * Generate the coefficients used to construct the polynomial randomly.
   *
   * <p>The coefficients are randomly generated in the range (2^((length of p) - 2) to 2^((length of
   * p) - 1)) offset to (p - 1) - 2^((length of p) - 1).
   *
   * <p>What this does is ensure the coefficients are large. This is important as the security of
   * the system assumes large numbers whcih cause modulo arithmetic operations.
   *
   * @param numCoefficients The number of coefficients to generate.
   * @return An array containing the coefficients.
   */
  public BigInteger[] generateRandomCoefficients(final int numCoefficients) {
    BigInteger[] coefficients = new BigInteger[numCoefficients];
    int pLength = prime.bitLength();
    int coefficientSize = (pLength + 7) / 8;
    byte[] coefficientData = new byte[coefficientSize];
    byte[] maxCandidateData = new byte[coefficientSize];

    // The generated bits will always be 1 bit shorter than p. With the most significant
    // bit set to 1. This ensures that the coefficients are large.
    // Use bit masks to make the generated data the correct length.
    byte maxBitMask = 0x00;
    byte maxByteMask = 0x00;
    int msBits = (pLength - 1) % 8;
    for (int i = 0; i < msBits; i++) {
      if (i == 0) {
        maxBitMask = 0x01;
      } else {
        maxBitMask = (byte) (maxBitMask << 1);
      }
      maxByteMask |= maxBitMask;
    }

    // Create the max possible generated coefficient
    for (int i = 0; i < coefficientSize; i++) {
      maxCandidateData[i] = (byte) 0xFF;
    }
    maxCandidateData[0] = maxByteMask;

    // Calculate the difference between the value of the max generated data and p-1.
    BigInteger maxCandidate = new BigInteger(1, maxCandidateData);
    BigInteger offset = prime.subtract(maxCandidate);
    offset = offset.subtract(BigInteger.ONE);

    for (int i = 0; i < numCoefficients; i++) {
      // Generate a random coefficient.
      this.random.nextBytes(coefficientData);
      // Remove extra bits
      coefficientData[0] &= maxByteMask;
      // Set msb to 1
      coefficientData[0] |= maxBitMask;
      coefficients[i] = new BigInteger(1, coefficientData);
      // Add the offset to make the maximum value p-1.
      coefficients[i] = coefficients[i].add(offset);
    }
    return coefficients;
  }

  /**
   * Generates Y values given X values using a polynomial which is based on the threshold and a
   * secret value. <code>threshold-1</code> is the degree of the polynomial. The secret is the Y
   * intercept (x=0 value).
   *
   * <p>For <code>threshold = 3</code>, the polynomial is given by:<br>
   * y = secret + a * x + b * x**2
   *
   * <p>The number of shares that the secret is split into is given by the number of X values.
   *
   * @param secret The value to protect. This will be the x=0 value.
   * @param xValues The x coordinates of the shares. Note that none of them should be zero. All
   *     xValues should be unique.
   * @param coefficients The coefficients of the polynomial. All the values should be smaller than
   *     p. There should be threshold-1 coefficients.
   * @return SecretShares containing x and y values.
   */
  public IntegerSecretShare[] generateSecretShares(
      final BigInteger secret, final BigInteger[] xValues, final BigInteger[] coefficients) {
    BigInteger[] yValues = generateShares(secret, xValues, coefficients);

    IntegerSecretShare[] retval = new IntegerSecretShare[xValues.length];
    for (int i = 0; i < xValues.length; i++) {
      retval[i] = new IntegerSecretShare(xValues[i], yValues[i]);
    }
    return retval;
  }

  public IntegerSecretShare[] generateSecretShares(
      final BigInteger[] xValues, final BigInteger[] coefficients) {
    BigInteger[] yValues = generateShares(xValues, coefficients);

    IntegerSecretShare[] retval = new IntegerSecretShare[xValues.length];
    for (int i = 0; i < xValues.length; i++) {
      retval[i] = new IntegerSecretShare(xValues[i], yValues[i]);
    }
    return retval;
  }

  /**
   * Generates Y values given X values using a polynomial which is based on the coefficients and
   * secret value. <code>threshold-1</code> is the degree of the polynomial. The secret is the Y
   * intercept (x=0 value).
   *
   * <p>For <code>threshold = 3</code>, the polynomial is given by:<br>
   * y = secret + a * x + b * x**2
   *
   * <p>The number of shares that the secret is split into is given by the number of X values.
   *
   * @param secret The value to protect. This will be the x=0 value.
   * @param xValues The x coordinates of the shares. Note that none of them should be zero. All
   *     xValues should be unique.
   * @param coefficients The coefficients of the polynomial. All the values should be smaller than
   *     p. There should be threshold-1 coefficients.
   * @return The y coordinates of the shares.
   */
  public BigInteger[] generateShares(
      final BigInteger secret, final BigInteger[] xValues, final BigInteger[] coefficients) {
    BigInteger[] coefs = new BigInteger[coefficients.length + 1];
    for (int i = 0; i < coefficients.length; i++) {
      coefs[i] = coefficients[i];
    }
    coefs[coefs.length - 1] = secret;
    return generateShares(xValues, coefs);
  }

  /**
   * Calculate the y values given a set of x value and coefficients to an equation.
   *
   * <p>Rather than calculate y using: y = a * x * x * x + b * x * x + c * x+ d
   *
   * <p>We use: y = (((a) * x + b) * x + c) * x + d
   *
   * <p>That is: <br>
   * y = (a) <br>
   * y = y * x + b <br>
   * y = y * x + c <br>
   * y = y * x + d
   *
   * @param xValues The x values to calculate y values for.
   * @param coefficients The coefficients to the curve.
   * @return y values which match the x values.
   */
  public BigInteger[] generateShares(final BigInteger[] xValues, final BigInteger[] coefficients) {
    // Calculate the Y values given the X values and coefficients.
    int numShares = xValues.length;
    BigInteger[] yVals = new BigInteger[numShares];
    for (int i = 0; i < numShares; i++) {
      BigInteger accumulator = coefficients[0];

      for (int j = 1; j < coefficients.length; j++) {
        accumulator = accumulator.multiply(xValues[i]);
        accumulator = accumulator.add(coefficients[j]);
        accumulator = accumulator.mod(this.prime);
      }

      yVals[i] = accumulator;
    }

    return yVals;
  }

  /**
   * Calculate BLS points for a single x value based on coefficients.
   *
   * @param xValue The x value to calculate the point for.
   * @param coefPublic The coefficients to the curve.
   * @return The point which corresponds to the x value.
   */
  public BlsPoint generatePublicKeyShare(final BigInteger xValue, final BlsPoint[] coefPublic) {
    BlsPoint yValAccumulator = coefPublic[0];
    for (int j = 1; j < coefPublic.length; j++) {
      yValAccumulator = yValAccumulator.scalarMul(xValue);
      yValAccumulator = yValAccumulator.add(coefPublic[j]);
    }
    return yValAccumulator;
  }

  /**
   * Calculate the secret from the shares.
   *
   * @param shares Shares used to reconstruct the coefficients of the equation.
   * @return The y value at x=0.
   */
  public BigInteger calculateSecret(final IntegerSecretShare[] shares) {
    return calculateShare(shares, BigInteger.ZERO);
  }

  /**
   * Regenerate a Y value for a certain X value, based on a set of X values and Y values.
   *
   * <p>The secret is recovered using Lagrange basis polynomials.
   *
   * <p>Note that the wrong secret will be generated if the values are not correct.
   *
   * <p>The Lagrange basis polynomials are of the form:
   *
   * <pre>
   *       x - x1      x - x2     x - x3
   * L0 = -------  *  -------  *  -------  *  ....
   *      x0 - x1     x0 - x2     x0 - x3
   *
   *       x - x0      x - x2      x - x3
   * L1 = -------  *  -------  *  -------  *  ....
   *      x1 - x0     x1 - x2     x1 - x3
   *
   *       x - x0      x - x1      x - x3
   * L2 = -------  *  -------  *  -------  *  ....
   *      x2 - x0     x2 - x1     x2 - x3
   *
   *          x - x0        x - x1        x - x3                       x - xi
   * Ln-1 = ---------  *  ---------  *  ---------  *  ....  where the ------- term is not included.
   *        xn-1 - x0     xn-1 - x1     xn-1 - x3                     xi - xi
   *
   * y(x) = Sum for all j ( y(j) * L(j))
   * </pre>
   *
   * To recover the secret, y is calculated at x=0. To create a new share use a new x value.
   *
   * @param shares Shares to use to reconstruct coefficients.
   * @param x The x value. If x=0 then the secret is recovered, otherwise a new share is created.
   * @return The recovered secret.
   */
  public BigInteger calculateShare(final IntegerSecretShare[] shares, final BigInteger x) {
    int numShares = shares.length;
    if (numShares < threshold) {
      throw new RuntimeException("not enough shares to recover the secret");
    }
    if (numShares != threshold) {
      throw new RuntimeException("Why are too many shares being provided?");
    }

    BigInteger[] xValues = new BigInteger[shares.length];
    BigInteger[] yValues = new BigInteger[shares.length];
    for (int i = 0; i < shares.length; i++) {
      xValues[i] = shares[i].getShareX();
      yValues[i] = shares[i].getShareY();
    }

    // Compute the Lagrange basis polynomials L0 to Ln-1.
    BigInteger[] lagrange = new BigInteger[numShares];
    for (int i = 0; i < numShares; i++) {
      BigInteger numerator = BigInteger.ONE;
      BigInteger denominator = BigInteger.ONE;
      for (int j = 0; j < numShares; j++) {
        if (j != i) {
          // Numerator = (x-x0) * (x-x1) * (x-x2) * ...
          BigInteger temp1 = x.subtract(xValues[j]);
          BigInteger temp2 = temp1.mod(prime);
          BigInteger mult1 = numerator.multiply(temp2);
          numerator = mult1.mod(prime);

          // Denominator = (xi-x0) * (xi-x1) * (xi-x2) * ...
          BigInteger temp3 = xValues[i].subtract(xValues[j]);
          BigInteger temp4 = temp3.mod(prime);
          BigInteger mult2 = denominator.multiply(temp4);
          denominator = mult2.mod(prime);
        }
      }

      // Li = numerator / denominator
      BigInteger temp5 = denominator.modInverse(prime);
      BigInteger temp6 = numerator.multiply(temp5);
      lagrange[i] = temp6.mod(prime);
    }

    // recovered = (y0 * L0) + (y1 * L1) +...
    BigInteger recovered = BigInteger.ZERO;
    for (int i = 0; i < numShares; i++) {
      BigInteger mult3 = yValues[i].multiply(lagrange[i]);
      BigInteger temp7 = mult3.add(recovered);
      recovered = temp7.mod(prime);
    }
    return recovered;
  }

  /**
   * Calculate the secret from the shares.
   *
   * @param shares Shares used to reconstruct the coefficients of the equation.
   * @return The y value at x=0.
   */
  public BlsPoint calculateSecret(final BlsPointSecretShare[] shares) {
    return calculateShare(shares, BigInteger.ZERO);
  }

  public BlsPoint calculateShare(final BlsPointSecretShare[] shares, final BigInteger x) {
    int numShares = shares.length;
    if (numShares < threshold) {
      throw new RuntimeException("not enough shares to recover the secret");
    }
    if (numShares != threshold) {
      throw new RuntimeException("Why are too many shares being provided?");
    }

    BigInteger[] xValues = new BigInteger[shares.length];
    BlsPoint[] yValues = new BlsPoint[shares.length];
    for (int i = 0; i < shares.length; i++) {
      xValues[i] = shares[i].getShareX();
      yValues[i] = shares[i].getShareY();
    }

    // Compute the Lagrange basis polynomials L0 to Ln-1.
    BigInteger[] lagrange = new BigInteger[numShares];
    for (int i = 0; i < numShares; i++) {
      BigInteger numerator = BigInteger.ONE;
      BigInteger denominator = BigInteger.ONE;
      for (int j = 0; j < numShares; j++) {
        if (j != i) {
          // Numerator = (x-x0) * (x-x1) * (x-x2) * ...
          BigInteger temp1 = x.subtract(xValues[j]);
          BigInteger temp2 = temp1.mod(prime);
          BigInteger mult1 = numerator.multiply(temp2);
          numerator = mult1.mod(prime);

          // Denominator = (xi-x0) * (xi-x1) * (xi-x2) * ...
          BigInteger temp3 = xValues[i].subtract(xValues[j]);
          BigInteger temp4 = temp3.mod(prime);
          BigInteger mult2 = denominator.multiply(temp4);
          denominator = mult2.mod(prime);
        }
      }

      // Li = numerator / denominator
      BigInteger temp5 = denominator.modInverse(prime);
      BigInteger temp6 = numerator.multiply(temp5);
      lagrange[i] = temp6.mod(prime);
    }

    // recovered = (y0 * L0) + (y1 * L1) +...
    BlsPoint recovered = null;
    for (int i = 0; i < numShares; i++) {
      BlsPoint mult3 = yValues[i].scalarMul(lagrange[i]);
      if (recovered == null) {
        recovered = mult3;
      } else {
        recovered = recovered.add(mult3);
      }
    }
    return recovered;
  }
}
