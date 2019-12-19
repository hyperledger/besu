package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.enclavePublicKey;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.junit.Test;

public class MultiTenancyUserUtilTest {

  @Test
  public void noEnclavePublicKeyWhenNoUserProvided() {
    assertThat(enclavePublicKey(empty())).isEmpty();
  }

  @Test
  public void noEnclavePublicKeyWhenUserWithoutEnclavePublicKeyClaimProvided() {
    final JsonObject token = new JsonObject();
    final Optional<User> user = Optional.of(new JWTUser(token, ""));

    assertThat(enclavePublicKey(user)).isEmpty();
  }

  @Test
  public void enclavePublicKeyKeyReturnedForUserWithEnclavePublicKeyClaim() {
    final JsonObject principle = new JsonObject();
    principle.put("privacyPublicKey", "ABC123");
    final Optional<User> user = Optional.of(new JWTUser(principle, ""));

    assertThat(enclavePublicKey(user)).contains("ABC123");
  }
}
