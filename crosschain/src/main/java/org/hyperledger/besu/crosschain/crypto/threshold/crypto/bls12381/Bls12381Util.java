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

import java.math.BigInteger;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;

public class Bls12381Util {

  /* Convert a java BigInteger to a milagro BIG */
  public static org.apache.milagro.amcl.BLS381.BIG BIGFromBigInteger(final BigInteger bi) {

    byte[] biBytes = bi.toByteArray();
    byte[] bigBytes = new byte[BIG.MODBYTES];
    if (biBytes.length >= BIG.MODBYTES) System.arraycopy(biBytes, 0, bigBytes, 0, BIG.MODBYTES);
    else System.arraycopy(biBytes, 0, bigBytes, BIG.MODBYTES - biBytes.length, biBytes.length);

    org.apache.milagro.amcl.BLS381.BIG b = org.apache.milagro.amcl.BLS381.BIG.fromBytes(bigBytes);
    return (b);
  }

  /* Convert milagro BIG to a java BigInteger */
  public static BigInteger BigIntegerFromBIG(final org.apache.milagro.amcl.BLS381.BIG big) {
    byte[] bigBytes = new byte[BIG.MODBYTES];

    big.tobytearray(bigBytes, 0);
    BigInteger b = new BigInteger(bigBytes);

    return (b);
  }

  /* Convert a java BigInteger to a milagro DBIG */
  public static org.apache.milagro.amcl.BLS381.DBIG DBIGFromBigInteger(final BigInteger bi) {
    BIG b = BIGFromBigInteger(bi);
    DBIG d = new DBIG(b);

    return (d);
  }
}
