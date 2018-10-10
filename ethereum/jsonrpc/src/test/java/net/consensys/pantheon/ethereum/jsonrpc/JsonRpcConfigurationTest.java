package net.consensys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;

import com.google.common.collect.Lists;
import org.junit.Test;

public class JsonRpcConfigurationTest {

  @Test
  public void defaultConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    assertThat(configuration.isEnabled()).isFalse();
    assertThat(configuration.getHost()).isEqualTo("127.0.0.1");
    assertThat(configuration.getPort()).isEqualTo(8545);
    assertThat(configuration.getCorsAllowedDomains()).isEmpty();
    assertThat(configuration.getRpcApis())
        .containsExactlyInAnyOrder(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);
  }

  @Test
  public void corsAllowedOriginsDefaultShouldBeEmptyList() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    assertThat(configuration.getCorsAllowedDomains()).isEmpty();
  }

  @Test
  public void rpcApiDefaultShouldBePredefinedList() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    assertThat(configuration.getRpcApis()).containsExactly(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);
  }

  @Test
  public void settingCorsAllowedOriginsShouldOverridePreviousValues() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    configuration.setCorsAllowedDomains(Lists.newArrayList("foo", "bar"));
    assertThat(configuration.getCorsAllowedDomains()).containsExactly("foo", "bar");

    configuration.setCorsAllowedDomains(Lists.newArrayList("zap"));
    assertThat(configuration.getCorsAllowedDomains()).containsExactly("zap");
  }

  @Test
  public void settingRpcApisShouldOverridePreviousValues() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    configuration.setRpcApis(Lists.newArrayList(RpcApis.ETH, RpcApis.MINER));
    assertThat(configuration.getRpcApis()).containsExactly(RpcApis.ETH, RpcApis.MINER);

    configuration.setRpcApis(Lists.newArrayList(RpcApis.DEBUG));
    assertThat(configuration.getRpcApis()).containsExactly(RpcApis.DEBUG);
  }
}
