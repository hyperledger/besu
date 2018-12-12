/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.config;

import static java.util.stream.Collectors.toList;

import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DiscoveryConfiguration {
  public static List<Peer> MAINNET_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://a979fb575495b8d6db44f750317d0f4622bf4c2aa3365d6af7c284339968eef29b69ad0dce72a4d8db5ebb4968de0e3bec910127f134779fbcb0cb6d3331163c@52.16.188.185:30303",
                  "enode://3f1d12044546b76342d59d4a05532c14b85aa669704bfe1f864fe079415aa2c02d743e03218e57a33fb94523adb54032871a6c51b2cc5514cb7c7e35b3ed0a99@13.93.211.84:30303",
                  "enode://78de8a0916848093c73790ead81d1928bec737d565119932b98c6b100d944b7a95e94f847f689fc723399d2e31129d182f7ef3863f2b4c820abbf3ab2722344d@191.235.84.50:30303",
                  "enode://158f8aab45f6d19c6cbf4a089c2670541a8da11978a2f90dbf6a502a4a3bab80d288afdbeb7ec0ef6d92de563767f3b1ea9e8e334ca711e9f8e2df5a0385e8e6@13.75.154.138:30303",
                  "enode://1118980bf48b0a3640bdba04e0fe78b1add18e1cd99bf22d53daac1fd9972ad650df52176e7c7d89d1114cfef2bc23a2959aa54998a46afcf7d91809f0855082@52.74.57.123:30303",
                  "enode://979b7fa28feeb35a4741660a16076f1943202cb72b6af70d327f053e248bab9ba81760f39d0701ef1d8f89cc1fbd2cacba0710a12cd5314d5e0c9021aa3637f9@5.1.83.226:30303")
              .map(DefaultPeer::fromURI)
              .collect(toList()));
  public static List<Peer> RINKEBY_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://a24ac7c5484ef4ed0c5eb2d36620ba4e4aa13b8c84684e1b4aab0cebea2ae45cb4d375b77eab56516d34bfbd3c1a833fc51296ff084b770b94fb9028c4d25ccf@52.169.42.101:30303",
                  "enode://343149e4feefa15d882d9fe4ac7d88f885bd05ebb735e547f12e12080a9fa07c8014ca6fd7f373123488102fe5e34111f8509cf0b7de3f5b44339c9f25e87cb8@52.3.158.184:30303",
                  "enode://b6b28890b006743680c52e64e0d16db57f28124885595fa03a562be1d2bf0f3a1da297d56b13da25fb992888fd556d4c1a27b1f39d531bde7de1921c90061cc6@159.89.28.211:30303")
              .map(DefaultPeer::fromURI)
              .collect(toList()));
  public static List<Peer> ROPSTEN_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303",
                  "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303")
              .map(DefaultPeer::fromURI)
              .collect(toList()));

  public static List<Peer> GOERLI_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://04fb7acb86f47b64298374b5ccb3c2959f1e5e9362158e50e0793c261518ffe83759d8295ca4a88091d4726d5f85e6276d53ae9ef4f35b8c4c0cc6b99c8c0537@40.70.214.166:40303",
                  "enode://17de5580bbc1620081a21f82954731c7854305463630a0d677ed991487609829a6bf1ffcb8fb8ef269eff4829690625db176b498c629b9b13cb39b73b6e7b08b@213.186.16.82:1345",
                  "enode://22da3ef3707626a92a32b0527d0846f88228daa0536c62d83c9ac7e96660bc8e4ac70a9aa8f8cedf71b580cd41449ad46c6e5a06ecf138b142f38a9d1b2b856a@85.7.110.224:30303",
                  "enode://3897b1a5786948f643d9755df92dc56d0b2284f36730dc198ef371aebf191b24b5cbe8162c2032b09b2f14ba73460bfc3f7d4ef1e26bcc59297d4f235dc5cdc5@54.88.169.219:30303",
                  "enode://3d197d65ed92af6d0adf280ce486714fb641ef9f9f38f0bdd5ddd552666fc1132f033eb249a87f7f30086902c131f30f054f872ae80ac83eea6bd3760a7bbce2@40.70.214.166:30405",
                  "enode://3d8d6698d2d4d730d896c7c1e3602ff845343f71bacbf8cb614b0e94fcb3b10e1a49ac2a5063c76617182a1c5928a4a63d4be897e54ae1cb858a1b94d0d275b8@188.166.20.30:30303",
                  "enode://46add44b9f13965f7b9875ac6b85f016f341012d84f975377573800a863526f4da19ae2c620ec73d11591fa9510e992ecc03ad0751f53cc02f7c7ed6d55c7291@94.237.54.114:30313",
                  "enode://5065d5221b507764771a8b74abc69df0351217eae09b96ec0df4275576a8b2bbba9986ce3037e6fb3c933b5b301364e18030c1ada8cec4ae00f1fa4dfff32eb8@13.113.211.0:30303",
                  "enode://573b6607cd59f241e30e4c4943fd50e99e2b6f42f9bd5ca111659d309c06741247f4f1e93843ad3e8c8c18b6e2d94c161b7ef67479b3938780a97134b618b5ce@52.56.136.200:30303",
                  "enode://57f58f16fccdd9fb6f587565ac09af4b3b4b33d0fbd14252cc61d29a65b0d83c08419e67ac5292b9342090053526b847f2487278e609f4b4cd1dbf0f48105b2b@213.186.16.82:30303",
                  "enode://5d9b1cba03738dfd23e12e4efb99b72623474fece2cc582c95e3ba7d481d519dea0029901f1f844116bab806044e8552f0431b21cf8d96010fc351b483330faa@13.78.10.94:30405",
                  "enode://7232e76a13a1abdfd75138f6499c310593d3376eef990de3c86593c85be0c09010f69276bd0f33eb4f9fef7316b9af6fa3fe332878642a813f86f8fdf03a0989@23.233.68.118:30303",
                  "enode://7592caf086d4d443905508492f40145bb1a0883ef7cbb9906b613eba6b501806e4ba0545a8e576236408e5b050e752e80a58445fb0ff2699b5ba4e334f481e40@13.78.10.94:30303",
                  "enode://76850e0836d0074e060118bf57a627bfd8af3b59871fd16cb4d0ca826eda7a60b0e773f359335e5e3c6cea8a72b1efbf9a298a61b88d0c94ab1a6ea34f1d6c40@13.78.10.94:40303",
                  "enode://85e577ec505b70e45ee6556809864c5211a5f10a46149d4caf1c6952fcbbd7f950c9510351f50f14374cd110fdb314f5cf9dcda92b25b82c4f2a1d008f15cf06@80.187.104.237:30303",
                  "enode://87a7adc692793eb41918b74b7ba4aa9ec1b45a24917fd6e66118ffc9ffcac9d2672941fc10fd5a2d44e76d02628e273f861bf480311e31babd1ee211f5838e40@168.61.153.244:30405",
                  "enode://9b1274fc252261bd9d8687bdc37cc3768551b93c9f3a3b3df2f4c7bbe6d797fb8c2ea6fb398114b2c6c6889a8257c244dfc57c1bb7b578c15cc5cc81fc0b3f79@168.61.153.244:30303",
                  "enode://9bc25c32aaed85926a663563c8aa1c9abef6fb18e9282b7ae00584c9ed9ef8e353f18459b591c59b08f5f1ce692cf27cfdc5a0ff85312656aa65552e789f2315@40.74.91.252:40303",
                  "enode://9ddf3e1ade168b2eea2d917dc32faffc727d53f488c78b293a523fda880bcca0b072506cf1ee6e743618d43f52e192fadc5ef5b43203a7f8e27b93a299248e3e@40.74.91.252:30405",
                  "enode://9e1b905a47caa6f4379c550a91b19df94c633689ee0d5bd4b317b998b052f270b715379fe5d4340fcca19116465443a79adb90883ccfffaf150432b505ccf3fd@159.203.190.82:30312",
                  "enode://a899e1b4551eb4d6e906a1313b8ba52e89eeb13412f1da058fd5a0cf261c235cb42fa38cc6c21b0fd5f5bcc5c5daa06945ea0410071cf34468a2f428454682ed@40.70.214.166:30303",
                  "enode://ac1977d8753fc12dd801e87139c0fd56fed3bac136c8a9cafe4f82e147612f3232266e7f4c74d869722fd26ecefff42496ea7b2225d624d432c123f5f4ed44b8@80.187.104.212:30303",
                  "enode://b0e75e7306435b7986567dafc8d9ca804b09a6f4b68fa1a6a2a5cb9979845b1dfd210c877dadfda0cebaf4ceb111114392df4bd50e223e71de3e807055707f19@86.103.218.43:30308",
                  "enode://b1668e8808fe1cd0c518a3ea8090f5412f62dc0cee64dbdec21dc3894aa32838b399e411481f070994e09ce8fddaaaa433bc2f4ad277aa5973080832546df631@123.113.131.26:30300",
                  "enode://b16d3bf8d6fe582e4a30698e536247ed9218c0706b890d7120c771fe510e77a79907ec2db2d5a1a393cdcd972eacefc07bebaeb99de5f7b169cb6b90951d1799@213.32.72.208:30303",
                  "enode://bc5235d7f37062b9c51108b0178df95b5e1ae9d1e79b950f5e7445ad6d101175d3a72b5e1ea89e459e2a430cc1d997833b8f9fa4637901b77821d96d47d19dcc@46.101.231.8:30303",
                  "enode://cbe600c451966737d858d8d561a913bb47770d4e24c50968c0d24b833613afc61daf88a0e1dabde5de01fa9a06469c6daa2bbf0e53dd6485f5b67eb4d3d65ab8@68.183.116.121:30303",
                  "enode://d21512d5900be2f67092526411d6d2b45ec50d5b6af33ccf1c519f45aea53662ac33fe36e6f5966d740e6f4f1f5df51462ff496ff8192ef52b9fac0f7bd49de9@213.186.16.82:30303",
                  "enode://d686ec8bf4bf0b205e8888e207352e6585232395e998cc1910b33be479c8405352ae1fc56aea79b2482b1b2d89412dc81091aa67ce775e335cd0f7d9dbfdfba3@84.196.20.71:30303",
                  "enode://d6acd86efcbb12d18b4287b8695ebc4730e67b1096de826df4bc05392004d66f977cff2844a7ead0a12cad961aa8277b1e032ee2c92ca40b4a2d76dd409da4ac@159.89.119.66:30303",
                  "enode://de2b54a19c9d77f94626043e0fea0c0878d5aa8eb1b58afb5bb1ca4fdd91d68e4a13a108a8f1a30009b258eb7f7b35efa71dcbdfda01a9aa0de06bf3898580f9@147.75.62.159:40404",
                  "enode://ea26ccaf0867771ba1fec32b3589c0169910cb4917017dba940efbef1d2515ce864f93a9abc846696ebad40c81de7c74d7b2b46794a71de8f95a0d019f494ff3@168.61.153.255:40303",
                  "enode://ed70646a024612fa0db4fdb276a3add7ea322b13bec80dd1566186cc86cf9b853e1553eb0f49d3c4b9b37dc936f80e9ee0a2432b7b53f0b0d792fc2cdfb62861@88.19.163.180:30303",
                  "enode://efaf6dad7a0773d911a6fcc44939faaba5d4802a7de8514bfacf9cc1ec9c292c82c1741eb4f14010895a273e7c94703cfc10c06068f6daa6ad25d4b0c0ca8e33@40.74.91.252:30303",
                  "enode://fede36ae9fb2347204b80e6e81cbe226eb6c6f2052e5fea8e4a224dcd69f3af062ca281064b9001d497f63d85447fef4c48018319654b04f9cd9ce619baedc23@86.103.218.43:30308")
              .map(DefaultPeer::fromURI)
              .collect(toList()));

  private boolean active = true;
  private String bindHost = "0.0.0.0";
  private int bindPort = 30303;
  private String advertisedHost = "127.0.0.1";
  private int bucketSize = 16;
  private List<Peer> bootstrapPeers = new ArrayList<>();

  public static DiscoveryConfiguration create() {
    return new DiscoveryConfiguration();
  }

  public String getBindHost() {
    return bindHost;
  }

  public DiscoveryConfiguration setBindHost(final String bindHost) {
    this.bindHost = bindHost;
    return this;
  }

  public int getBindPort() {
    return bindPort;
  }

  public DiscoveryConfiguration setBindPort(final int bindPort) {
    this.bindPort = bindPort;
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public DiscoveryConfiguration setActive(final boolean active) {
    this.active = active;
    return this;
  }

  public List<Peer> getBootstrapPeers() {
    return bootstrapPeers;
  }

  public DiscoveryConfiguration setBootstrapPeers(final Collection<?> bootstrapPeers) {
    if (bootstrapPeers.stream().allMatch(URI.class::isInstance)) {
      this.bootstrapPeers =
          bootstrapPeers.stream().map(URI.class::cast).map(DefaultPeer::fromURI).collect(toList());
    } else if (bootstrapPeers.stream().allMatch(Peer.class::isInstance)) {
      this.bootstrapPeers = bootstrapPeers.stream().map(Peer.class::cast).collect(toList());
    } else {
      throw new IllegalArgumentException("Expected a list of Peers or a list of enode URIs");
    }
    return this;
  }

  public String getAdvertisedHost() {
    return advertisedHost;
  }

  public DiscoveryConfiguration setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
    return this;
  }

  public int getBucketSize() {
    return bucketSize;
  }

  public DiscoveryConfiguration setBucketSize(final int bucketSize) {
    this.bucketSize = bucketSize;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DiscoveryConfiguration)) {
      return false;
    }
    final DiscoveryConfiguration that = (DiscoveryConfiguration) o;
    return active == that.active
        && bindPort == that.bindPort
        && bucketSize == that.bucketSize
        && Objects.equals(bindHost, that.bindHost)
        && Objects.equals(advertisedHost, that.advertisedHost)
        && Objects.equals(bootstrapPeers, that.bootstrapPeers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, bindHost, bindPort, advertisedHost, bucketSize, bootstrapPeers);
  }

  @Override
  public String toString() {
    return "DiscoveryConfiguration{"
        + "active="
        + active
        + ", bindHost='"
        + bindHost
        + '\''
        + ", bindPort="
        + bindPort
        + ", advertisedHost='"
        + advertisedHost
        + '\''
        + ", bucketSize="
        + bucketSize
        + ", bootstrapPeers="
        + bootstrapPeers
        + '}';
  }
}
