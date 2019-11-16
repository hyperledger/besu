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

package org.hyperledger.besu.nat.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.nat.core.domain.NATMethod;
import org.hyperledger.besu.nat.core.domain.NATPortMapping;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AbstractNATSystemTest {

  @Test
  public void assertThatSystemIsStartedAfterStart() {
    final AbstractNATSystem natSystem = buildNATSystem(NATMethod.DOCKER);
    assertThat(natSystem.isStarted()).isFalse();
    natSystem.start();
    assertThat(natSystem.isStarted()).isTrue();
  }

  @Test
  public void assertThatSystemIsStoppedAfterStopped() {
    final AbstractNATSystem natSystem = buildNATSystem(NATMethod.DOCKER);
    assertThat(natSystem.isStarted()).isFalse();
    natSystem.start();
    assertThat(natSystem.isStarted()).isTrue();
    natSystem.stop();
    assertThat(natSystem.isStarted()).isFalse();
  }

  @Test
  public void assertThatDoStartIsCalledOnlyOnce() {
    final AbstractNATSystem natSystem = Mockito.spy(buildNATSystem(NATMethod.DOCKER));
    natSystem.start();
    natSystem.start();
    verify(natSystem, times(2)).start();
    verify(natSystem).doStart();
  }

  @Test
  public void assertThatDoStopIsCalledOnlyOnce() {
    final AbstractNATSystem natSystem = Mockito.spy(buildNATSystem(NATMethod.DOCKER));
    natSystem.start();
    natSystem.stop();
    natSystem.stop();
    verify(natSystem).start();
    verify(natSystem).doStart();
    verify(natSystem, times(2)).stop();
    verify(natSystem).doStop();
  }

  @Test
  public void assertThatRequireSystemStartedThrowExceptionIfNotStarted() {
    assertThatThrownBy(() -> buildNATSystem(NATMethod.DOCKER).requireSystemStarted())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NAT system must be started.");
  }

  @Test
  public void assertThatSystemReturnValidNatMethod() {
    assertThat(buildNATSystem(NATMethod.DOCKER).getNatMethod()).isEqualTo(NATMethod.DOCKER);
  }

  @Test
  public void assertThatSystemReturnValidLocalIpAddress()
      throws UnknownHostException, ExecutionException, InterruptedException {
    final String hostAddress = InetAddress.getLocalHost().getHostAddress();
    assertThat(buildNATSystem(NATMethod.DOCKER).getLocalIPAddress().get()).isEqualTo(hostAddress);
  }

  private static AbstractNATSystem buildNATSystem(final NATMethod natMethod) {
    return new AbstractNATSystem(natMethod) {
      @Override
      public void doStart() {}

      @Override
      public void doStop() {}

      @Override
      protected CompletableFuture<String> retrieveExternalIPAddress() {
        return new CompletableFuture<>();
      }

      @Override
      public CompletableFuture<List<NATPortMapping>> getPortMappings() {
        return new CompletableFuture<>();
      }
    };
  }
}
