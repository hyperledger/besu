/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.nat.NatMethod;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class NatOptionsTest extends CommandTestAbstract {

  @Test
  public void helpShouldDisplayNatMethodInfo() {
    parseCommand("--help");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).contains("--nat-method");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodPropertyDefaultIsAuto() {
    parseCommand();

    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyDefaultIsBesu() {
    parseCommand();

    verify(mockRunnerBuilder).natManagerServiceName(eq(DEFAULT_BESU_SERVICE_NAME_FILTER));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodOptionIsParsedCorrectly() {

    parseCommand("--nat-method", "NONE");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.NONE));

    parseCommand("--nat-method", "UPNP");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.UPNP));

    parseCommand("--nat-method", "UPNPP2PONLY");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.UPNPP2PONLY));

    parseCommand("--nat-method", "AUTO");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    parseCommand("--nat-method", "DOCKER");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.DOCKER));

    parseCommand("--nat-method", "KUBERNETES");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.KUBERNETES));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyIsCorrectlyUpdated() {
    final String podName = "besu-updated";
    parseCommand("--Xnat-kube-service-name", podName);

    verify(mockRunnerBuilder).natManagerServiceName(eq(podName));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatDockerMethod() {
    parseCommand("--nat-method", "DOCKER", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatNoneMethod() {
    parseCommand("--nat-method", "NONE", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithKubernetes() {

    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithDocker() {

    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithUpnp() {

    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledCannotBeUsedWithAutoMethod() {
    parseCommand("--nat-method", "AUTO", "--Xnat-method-fallback-enabled", "false");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-method-fallback-enabled` parameter cannot be used in AUTO mode. Either remove --Xnat-method-fallback-enabled or select another mode (via --nat--method=XXXX)");
  }

  @Test
  public void parsesInvalidNatMethodOptionsShouldFail() {

    parseCommand("--nat-method", "invalid");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--nat-method': expected one of [UPNP, UPNPP2PONLY, DOCKER, KUBERNETES, AUTO, NONE] (case-insensitive) but was 'invalid'");
  }
}
