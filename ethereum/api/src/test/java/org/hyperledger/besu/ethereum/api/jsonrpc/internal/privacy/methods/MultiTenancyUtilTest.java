package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.junit.Test;

public class MultiTenancyUtilTest {

  @Test
  public void noEnclavePublicKeyWhenNoUserProvided() {
    assertThat(MultiTenancyUtil.enclavePublicKey(Optional.empty())).isEmpty();
  }

  @Test
  public void noEnclavePublicKeyWhenUserWithoutEnclavePublicKeyClaimProvided() {
    final JsonObject token = new JsonObject();
    final JWTUser user = new JWTUser(token, "");

    assertThat(MultiTenancyUtil.enclavePublicKey(Optional.of(user)).isEmpty());
  }

  @Test
  public void enclavePublicKeyKeyReturnedForUserWithEnclavePublicKeyClaim() {
    final JsonObject principle = new JsonObject();
    principle.put("privacyPublicKey", "ABC123");
    final JWTUser user = new JWTUser(principle, "");

    assertThat(MultiTenancyUtil.enclavePublicKey(Optional.of(user))).contains("ABC123");
  }
}
