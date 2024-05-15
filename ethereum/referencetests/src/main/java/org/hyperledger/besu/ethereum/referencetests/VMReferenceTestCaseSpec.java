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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.evm.worldstate.WorldState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/**
 * A VM test case specification.
 *
 * <p>Note: this class will be auto-generated with the JSON test specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VMReferenceTestCaseSpec {

  /** The environment information to execute. */
  private final EnvironmentInformation exec;

  /** The VM output. */
  private final Bytes out;

  private final long finalGas;

  private final ReferenceTestWorldState initialWorldState;

  private final boolean exceptionalHaltExpected;

  private final ReferenceTestWorldState finalWorldState;

  @JsonCreator
  public VMReferenceTestCaseSpec(
      @JsonProperty("exec") final EnvironmentInformation exec,
      @JsonProperty("env") final ReferenceTestEnv env,
      @JsonProperty("gas") final String finalGas,
      @JsonProperty("out") final String out,
      @JsonProperty("pre") final ReferenceTestWorldState initialWorldState,
      @JsonProperty("post") final ReferenceTestWorldState finalWorldState) {
    this.exec = exec;
    this.initialWorldState = initialWorldState;
    this.initialWorldState.persist(null);
    exec.setBlockHeader(env);

    if (finalGas != null && out != null && finalWorldState != null) {
      this.finalGas = Long.decode(finalGas);
      this.finalWorldState = finalWorldState;
      this.out = Bytes.fromHexString(out);
      this.exceptionalHaltExpected = false;
    } else {
      this.exceptionalHaltExpected = true;
      // These values should never be checked if this is a test case that
      // exceptionally halts.
      this.finalGas = -1;
      this.finalWorldState = null;
      this.out = null;
    }
  }

  /**
   * Returns the environment information to execute.
   *
   * @return The environment information to execute.
   */
  public EnvironmentInformation getExec() {
    return exec;
  }

  /**
   * Returns the initial world state.
   *
   * @return The initial world state to use when setting up the test.
   */
  public WorldState getInitialWorldState() {
    return initialWorldState;
  }

  /**
   * Returns the final world state.
   *
   * @return The final world state to use when setting up the test.
   */
  public WorldState getFinalWorldState() {
    return finalWorldState;
  }

  public long getFinalGas() {
    return finalGas;
  }

  /**
   * Return the expected VM return value.
   *
   * @return The expected VM return value.
   */
  public Bytes getOut() {
    return out;
  }

  /**
   * Returns true if this test case should expect the VM to exceptionally halt; otherwise false.
   *
   * @return if an exceptional halt is expected
   */
  public boolean isExceptionHaltExpected() {
    return exceptionalHaltExpected;
  }
}
