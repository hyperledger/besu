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
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve;

public class SECP256R1 extends AbstractSECP256 {

  private static final Logger LOG = LogManager.getLogger();
  public static final String CURVE_NAME = "secp256r1";

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
}
