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
 *
 */
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

public interface LibEthPairings extends Library {

  LibEthPairings INSTANCE = findInstance();

  private static LibEthPairings findInstance() {
    try {
      System.setProperty(
          "jna.library.path",
          "/Users/dannoferrin/git/github.com/shemnon/matterlabs_eip1962/target/release");
      return Native.load("eth_pairings", LibEthPairings.class);
    } catch (final Throwable t) {
      return null;
    }
  }

  int EIP2537_PREALLOCATE_FOR_ERROR_BYTES = 256;

  int EIP2537_PREALLOCATE_FOR_RESULT_BYTES = 256;

  byte BLS12_G1ADD_OPERATION_RAW_VALUE = 1;
  byte BLS12_G1MUL_OPERATION_RAW_VALUE = 2;
  byte BLS12_G1MULTIEXP_OPERATION_RAW_VALUE = 3;
  byte BLS12_G2ADD_OPERATION_RAW_VALUE = 4;
  byte BLS12_G2MUL_OPERATION_RAW_VALUE = 5;
  byte BLS12_G2MULTIEXP_OPERATION_RAW_VALUE = 6;
  byte BLS12_PAIR_OPERATION_RAW_VALUE = 7;
  byte BLS12_MAP_OPERATION_RAW_VALUE = 8;

  int eip2537_perform_operation(
      byte op,
      byte[] i,
      int i_len,
      byte[] o,
      IntByReference o_len,
      byte[] err,
      IntByReference err_len);
}
