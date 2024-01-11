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
package org.hyperledger.besu.nat.upnp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.UpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.mockito.ArgumentCaptor;

public final class UpnpNatManagerTest {

  private UpnpService mockedService;
  private Registry mockedRegistry;
  private ControlPoint mockedControlPoint;

  private UpnpNatManager upnpManager;

  @BeforeEach
  public void initialize() {

    mockedRegistry = mock(Registry.class);
    mockedControlPoint = mock(ControlPoint.class);

    mockedService = mock(UpnpService.class);
    when(mockedService.getRegistry()).thenReturn(mockedRegistry);
    when(mockedService.getControlPoint()).thenReturn(mockedControlPoint);

    upnpManager = new UpnpNatManager(mockedService);
  }

  @Test
  public void startShouldInvokeUPnPService() throws Exception {
    upnpManager.start();

    verify(mockedService).startup();
    verify(mockedRegistry).addListener(notNull());
  }

  @Test
  public void stopShouldInvokeUPnPService() throws Exception {
    upnpManager.start();
    upnpManager.stop();

    verify(mockedRegistry).removeListener(notNull());
    verify(mockedService).shutdown();
  }

  @Test
  public void stopDoesNothingWhenAlreadyStopped() throws Exception {
    upnpManager.stop();

    verifyNoMoreInteractions(mockedService);
  }

  @Test
  public void startDoesNothingWhenAlreadyStarted() throws Exception {
    upnpManager.start();

    verify(mockedService).startup();
    verify(mockedService).getRegistry();

    upnpManager.start();

    verifyNoMoreInteractions(mockedService);
  }

  @Test
  public void requestPortForwardThrowsWhenCalledBeforeStart() throws Exception {

    assertThatThrownBy(
            () -> {
              upnpManager.requestPortForward(80, NetworkProtocol.TCP, NatServiceType.DISCOVERY);
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void requestPortForwardThrowsWhenPortIsZero() throws NatInitializationException {
    upnpManager.start();

    assertThatThrownBy(
            () -> upnpManager.requestPortForward(0, NetworkProtocol.TCP, NatServiceType.DISCOVERY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void queryIpThrowsWhenStopped() throws Exception {

    assertThatThrownBy(
            () -> {
              upnpManager.queryExternalIPAddress();
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void queryIpDoesNotThrowWhenStarted() throws Exception {
    upnpManager.start();

    upnpManager.queryExternalIPAddress();
  }

  @Test
  public void registryListenerShouldDetectService() throws Exception {
    upnpManager.start();

    ArgumentCaptor<RegistryListener> captor = ArgumentCaptor.forClass(RegistryListener.class);
    verify(mockedRegistry).addListener(captor.capture());
    RegistryListener listener = captor.getValue();

    assertThat(listener).isNotNull();

    // create a remote device that matches the WANIPConnection service that UpnpNatManager
    // is looking for and directly call the registry listener
    RemoteService wanIpConnectionService =
        new RemoteService(
            new UDAServiceType("WANIPConnection"),
            new UDAServiceId("WANIPConnectionService"),
            URI.create("/x_wanipconnection.xml"),
            URI.create("/control?WANIPConnection"),
            URI.create("/event?WANIPConnection"),
            null,
            null);

    RemoteDevice device =
        new RemoteDevice(
            new RemoteDeviceIdentity(
                UDN.valueOf(UpnpNatManager.SERVICE_TYPE_WAN_IP_CONNECTION),
                3600,
                new URL("http://127.63.31.15/"),
                null,
                InetAddress.getByName("127.63.31.15")),
            new UDADeviceType("WANConnectionDevice"),
            new DeviceDetails("WAN Connection Device"),
            wanIpConnectionService);

    listener.remoteDeviceAdded(mockedRegistry, device);

    assertThat(upnpManager.getWANIPConnectionService().join()).isEqualTo(wanIpConnectionService);
  }
}
