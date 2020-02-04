package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PrivacyGroupUtilTest {

  private static final String PRIVACY_GROUP_ID1 = "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=";
  private static final String PRIVACY_GROUP_ID2 = "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw=";
  private static final String PRIVACY_GROUP_ID3 = "6fg8q5rWMBoAT2oIiU3tYJbk4b7oAr7dxaaVY7TeM3U=";

  @Test
  public void generatesPrivacyGroupPrivateFromAndEmptyPrivateFor() {
    final String expected = "kAbelwaVW7okoEn1+okO+AbA4Hhz/7DaCOWVQz9nx5M=";
    assertThat(privacyGroupId(PRIVACY_GROUP_ID1)).isEqualTo(expected);
  }

  @Test
  public void generatesPrivacyGroupForDuplicatePrivateForValues() {
    assertThat(
            privacyGroupId(
                PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID1, PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID1))
        .isEqualTo("4F/ZlRBkBTUTZu9rhkidb+7Ol3np2jIRmKpwviMS9pU=");
    assertThat(
            privacyGroupId(
                PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID2))
        .isEqualTo("YLWh3AuGLOIB1oHH7R3lYVicEGlifmWq+SeD/Jit2HA=");
  }

  @Test
  public void generatesSamePrivacyGroupForPrivateForInDifferentOrders() {
    final String expectedPrivacyGroupId = "/xzRjCLioUBkm5LYuzll61GXyrD5x7bvXzQk/ovJA/4=";
    assertThat(privacyGroupId(PRIVACY_GROUP_ID1, PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID3))
        .isEqualTo(expectedPrivacyGroupId);
    assertThat(privacyGroupId(PRIVACY_GROUP_ID1, PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID2))
        .isEqualTo(expectedPrivacyGroupId);
    assertThat(privacyGroupId(PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID1, PRIVACY_GROUP_ID3))
        .isEqualTo(expectedPrivacyGroupId);
    assertThat(privacyGroupId(PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID1))
        .isEqualTo(expectedPrivacyGroupId);
    assertThat(privacyGroupId(PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID1, PRIVACY_GROUP_ID2))
        .isEqualTo(expectedPrivacyGroupId);
    assertThat(privacyGroupId(PRIVACY_GROUP_ID3, PRIVACY_GROUP_ID2, PRIVACY_GROUP_ID1))
        .isEqualTo(expectedPrivacyGroupId);
  }

  private String privacyGroupId(final String privateFrom, String... privateFor) {
    return PrivacyGroupUtil.generateEeaPrivacyGroup(
        Bytes.fromBase64String(privateFrom),
        Arrays.stream(privateFor).map(Bytes::fromBase64String).collect(Collectors.toList()));
  }
}
