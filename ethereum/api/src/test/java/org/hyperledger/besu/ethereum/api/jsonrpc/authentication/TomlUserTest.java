package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.list;

import java.util.Optional;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class TomlUserTest {

  @Test
  public void createsPrincipleWithAllValues() {
    final TomlUser tomlUser =
        new TomlUser(
            "user",
            "password",
            list("admin"),
            list("eth:*", "perm:*"),
            list("net"),
            Optional.of("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));

    final JsonObject principal = tomlUser.principal();
    assertThat(principal.getString("username")).isEqualTo("user");
    assertThat(principal.getString("password")).isEqualTo("password");
    assertThat(principal.getJsonArray("groups")).isEqualTo(new JsonArray(list("admin")));
    assertThat(principal.getJsonArray("permissions"))
        .isEqualTo(new JsonArray(list("eth:*", "perm:*")));
    assertThat(principal.getJsonArray("roles")).isEqualTo(new JsonArray(list("net")));
    assertThat(principal.getString("enclavePublicKey"))
        .isEqualTo("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
  }

  @Test
  public void createsPrincipleWithOptionalValues() {
    final TomlUser tomlUser =
        new TomlUser("user", "password", list(), list(), list(), Optional.empty());

    final JsonObject principal = tomlUser.principal();
    assertThat(principal.getString("username")).isEqualTo("user");
    assertThat(principal.getString("password")).isEqualTo("password");
    assertThat(principal.getJsonArray("groups")).isEqualTo(new JsonArray());
    assertThat(principal.getJsonArray("permissions")).isEqualTo(new JsonArray());
    assertThat(principal.getJsonArray("roles")).isEqualTo(new JsonArray());
    assertThat(principal.containsKey("enclavePublicKey")).isFalse();
  }
}
