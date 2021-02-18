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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.ibftlegacy;

import static org.junit.Assert.*;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class IbftExtraDataTest {

  @Test
  public void basicDecode() {
    final String input = "0x0000000000000000000000000000000000000000000000000000000000000000f858f85494e5447d0fa846b2e6f6006016ebaf9aa8f60701ae94381effc6c759bb7b967aa952f07eca7ff20a4f369498890479b2f3927ab0009bcb53f2429c6ab9976694d9186daa7e8b33c22b019962b48d80f98a46046080c0";
    final IbftExtraData ed = IbftExtraData.decodeRaw(Bytes.fromHexString(input));
  }

}