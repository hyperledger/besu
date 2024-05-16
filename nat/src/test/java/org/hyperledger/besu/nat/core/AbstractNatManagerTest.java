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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractNatManagerTest {

  @Test
  public void assertThatManagerIsStartedAfterStart() throws NatInitializationException {
    final AbstractNatManager natManager = buildNatManager(NatMethod.UPNP);
    assertThat(natManager.isStarted()).isFalse();
    natManager.start();
    assertThat(natManager.isStarted()).isTrue();
  }

  @Test
  public void assertThatManagerIsStoppedAfterStopped() throws NatInitializationException {
    final AbstractNatManager natManager = buildNatManager(NatMethod.UPNP);
    assertThat(natManager.isStarted()).isFalse();

    natManager.start();
    assertThat(natManager.isStarted()).isTrue();
    natManager.stop();
    assertThat(natManager.isStarted()).isFalse();
  }

  @Test
  public void assertThatDoStartIsCalledOnlyOnce() throws NatInitializationException {
    final AbstractNatManager natManager = Mockito.spy(buildNatManager(NatMethod.UPNP));
    natManager.start();
    natManager.start();
    verify(natManager, times(2)).start();
    verify(natManager).doStart();
  }

  @Test
  public void assertThatDoStopIsCalledOnlyOnce() throws NatInitializationException {
    final AbstractNatManager natManager = Mockito.spy(buildNatManager(NatMethod.UPNP));
    natManager.start();
    natManager.stop();
    natManager.stop();
    verify(natManager).start();
    verify(natManager).doStart();
    verify(natManager, times(2)).stop();
    verify(natManager).doStop();
  }

  @Test
  public void assertThatManagerReturnValidNatMethod() {
    assertThat(buildNatManager(NatMethod.UPNP).getNatMethod()).isEqualTo(NatMethod.UPNP);
  }

  @Test
  public void assertThatManagerReturnValidLocalIpAddress()
      throws UnknownHostException, ExecutionException, InterruptedException {
    final String hostAddress = InetAddress.getLocalHost().getHostAddress();
    assertThat(buildNatManager(NatMethod.UPNP).queryLocalIPAddress().get()).isEqualTo(hostAddress);
  }

  private static AbstractNatManager buildNatManager(final NatMethod natMethod) {
    return new AbstractNatManager(natMethod) {
      @Override
      public void doStart() {}

      @Override
      public void doStop() {}

      @Override
      protected CompletableFuture<String> retrieveExternalIPAddress() {
        return new CompletableFuture<>();
      }

      @Override
      public CompletableFuture<List<NatPortMapping>> getPortMappings() {
        return new CompletableFuture<>();
      }
    };
  }
}
