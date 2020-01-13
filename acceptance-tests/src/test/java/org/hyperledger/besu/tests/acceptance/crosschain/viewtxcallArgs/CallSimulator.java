/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.viewtxcallArgs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CallSimulator {
  List<BigInteger> arg;
  List<BigInteger> magicNumArr;
  BigInteger fooFlag;
  String str, barstr;
  byte[] a;

  void bar(final byte[] a, final String barstr, final boolean cond) {
    this.barstr = barstr;
    this.a = Arrays.copyOf(a, 32);
    arg = new ArrayList<BigInteger>();
    if (cond) {
      arg.add(BigInteger.valueOf(3));
    } else {
      arg.add(BigInteger.valueOf(6));
    }
    foo(arg, a, barstr);
  }

  BigInteger foo(final List<BigInteger> args1, final byte[] a, final String barstr) {
    return BigInteger.valueOf(
        args1.get((args1.size() - 1)).longValue() + new BigInteger(a).longValue());
  }

  void barUpdateState() {
    magicNumArr = new ArrayList<BigInteger>();
    magicNumArr.add(BigInteger.valueOf(256));
    magicNumArr.add(BigInteger.valueOf(257));
    magicNumArr.add(BigInteger.valueOf(258));
    str = "magic";
    updateState(magicNumArr, str);
  }

  void updateState(final List<BigInteger> magicNumArr, final String str) {
    fooFlag = BigInteger.valueOf(magicNumArr.get(0).longValue() + str.length());
  }
}
