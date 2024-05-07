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
package org.hyperledger.besu.ethereum.p2p.config;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DefaultDiscoveryConfiguration {
  public static final String SEPOLIA_DISCOVERY_URL =
      "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@all.sepolia.ethdisco.net";
  public static final String MAINNET_DISCOVERY_URL =
      "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@all.mainnet.ethdisco.net";

  public static final List<EnodeURL> MAINNET_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  // Ethereum Foundation Bootnodes
                  "enode://d860a01f9722d78051619d1e2351aba3f43f943f6f00718d1b9baa4101932a1f5011f16bb2b1bb35db20d6fe28fa0bf09636d26a87d31de9ec6203eeedb1f666@18.138.108.67:30303", // Singapore AWS
                  "enode://22a8232c3abc76a16ae9d6c3b164f98775fe226f0917b0ca871128a74a8e9630b458460865bab457221f1d448dd9791d24c4e5d88786180ac185df813a68d4de@3.209.45.79:30303", // Virginia AWS
                  "enode://2b252ab6a1d0f971d9722cb839a42cb81db019ba44c08754628ab4a823487071b5695317c8ccd085219c3a03af063495b2f1da8d18218da2d6a82981b45e6ffc@65.108.70.101:30303", // Helsinki Hetzner
                  "enode://4aeb4ab6c14b23e2c4cfdce879c04b0748a20d8e9b59e25ded2a08143e265c6c25936e74cbc8e641e3312ca288673d91f2f93f8e277de3cfa444ecdaaf982052@157.90.35.166:30303" // Falkenstein Hetzner
                  )
              .map(EnodeURLImpl::fromString)
              .collect(toList()));
  public static final List<EnodeURL> SEPOLIA_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://4e5e92199ee224a01932a377160aa432f31d0b351f84ab413a8e0a42f4f36476f8fb1cbe914af0d9aef0d51665c214cf653c651c4bbd9d5550a934f241f1682b@138.197.51.181:30303",
                  "enode://143e11fb766781d22d92a2e33f8f104cddae4411a122295ed1fdb6638de96a6ce65f5b7c964ba3763bba27961738fef7d3ecc739268f3e5e771fb4c87b6234ba@146.190.1.103:30303",
                  "enode://8b61dc2d06c3f96fddcbebb0efb29d60d3598650275dc469c22229d3e5620369b0d3dedafd929835fe7f489618f19f456fe7c0df572bf2d914a9f4e006f783a9@170.64.250.88:30303",
                  "enode://10d62eff032205fcef19497f35ca8477bea0eadfff6d769a147e895d8b2b8f8ae6341630c645c30f5df6e67547c03494ced3d9c5764e8622a26587b083b028e8@139.59.49.206:30303",
                  "enode://9e9492e2e8836114cc75f5b929784f4f46c324ad01daf87d956f98b3b6c5fcba95524d6e5cf9861dc96a2c8a171ea7105bb554a197455058de185fa870970c7c@138.68.123.152:30303")
              .map(EnodeURLImpl::fromString)
              .collect(toList()));
  public static final List<EnodeURL> CLASSIC_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  // ETC Cooperative
                  "enode://8e73168affd8d445edda09c561d607081ca5d7963317caae2702f701eb6546b06948b7f8687a795de576f6a5f33c44828e25a90aa63de18db380a11e660dd06f@159.203.37.80:30303",
                  "enode://2b1ef75e8b7119b6e0294f2e51ead2cf1a5400472452c199e9587727ada99e7e2b1199e36adcad6cbae65dce2410559546e4d83d8c93d45a559e723e56444c03@67.207.93.100:30303",
                  "enode://40b37d729fdc2c29620bbecc61264f450c3e849b5e60d1a362e6af04d84ae36e15120573498bcf56f97aeec18e530113a4d0b55b18e6a6523de8cf5a45e1db23@128.199.160.131:30303",
                  "enode://84e70a981a436efd7d6ae80b5d7ecb82636de77029635a2fb25ccf5d02106172d1cafdd7d8171a136d3fd545e5df94f4d63299eda7f8aec79967aa568ddaa71e@165.22.202.207:30303",
                  "enode://6ec7ac618a7147d8b0e41bc1e63080abfd56a48f50f06095c112e30f72cd5eee262b06aa168cb46ab470d56885f842a1ae44a3714bfb029ced83dab461852062@164.90.218.200:30303",
                  "enode://feba6c4bd757efce4fec0fa5775780d2c209e86231553a72738c2c4e95d0cfd7ac2189f6d30d3c059dd25d93ea060ca28f9936b564f7d04c4270bb60e8e1487b@178.128.171.230:30303",
                  "enode://c0b698f9fb1c5dd3b348c46dffbc524cebbfa48fd03c52523892d43d31d5fabacf4e27f215f5647c96190567975e423a5708cbdcd6441da2705bd85533e78eca@209.97.136.89:30303",
                  "enode://37b20dc1ca0ba428dc07f4bf87c548aa57b53e6883c607a041c88cc0f72c27ef3487b0e94f50e787fff8af5e2baf029cd6105461aa8ec250ccae93d6972107a9@134.209.30.110:30303",

                  // ETC Labs
                  "enode://66498ac935f3f54d873de4719bf2d6d61e0c74dd173b547531325bcef331480f9bedece91099810971c8567eeb1ae9f6954b013c47c6dc51355bbbbae65a8c16@54.148.165.1:30303",
                  "enode://73e74ce7426a17aa2d8b5bb64d796ec7dc4dcee2af9bbdd4434394d1e4e52e650b9e39202435fca29ce65c242fd6e59b93ed2cf37f04b645fb23e306273816ad@54.148.165.1:30304",
                  "enode://f1d4bde4cc8df2b8ce46194247450e54b9ba9e69410a30974a894a49273b10c069e7a1ff0ffd4921cb23af9f903d8257ed0133317850e670f2792f20e771a69e@123.57.29.99:30303",
                  "enode://7a7768a1603b6714acd16f646b84a5aff418869b5715fa606172d19e4e7d719699448b7707e30c7b59470b4fb8b38a020135641304861483a7dc3ba988e98490@47.108.52.30:30303",
                  "enode://903fceb08534c13fe1114b0c753a89c6c2ec50f973a85d308456c46721f8904b1cd47df9231114299be84e1e954db06552f791a0facbd86359aa9fd321d2ef50@47.240.106.205:30303",
                  "enode://c7ec057ad9450d2d5c4002e49e53e1d90142acd54128c89e794d11401de026b91aa0e84e3f679fb6b47f7940e08b5e7d21d8178c5fa6ba0f36971098cb566ea6@101.133.229.66:30303",
                  "enode://70b74fef51aa4f36330cc52ac04f16d38e1838f531f58bbc88365ca5fd4a3da6e8ec32316c43f79b157d0575faf53064fd925644d0f620b2b201b028c2b410d0@47.115.150.90:30303",
                  "enode://fa64d1fcb2d4cd1d1606cb940ea2b69fee7dc6c7a85ac4ad05154df1e9ae9616a6a0fa67a59cb15f79346408efa5a4efeba1e5993ddbf4b5cedbda27644a61cf@47.91.30.48:30303",

                  // @q9f ceibo
                  "enode://942bf2f0754972391467765be1d98206926fc8ad0be8a49cd65e1730420c37fa63355bddb0ae5faa1d3505a2edcf8fad1cf00f3c179e244f047ec3a3ba5dacd7@176.9.51.216:30355",
                  "enode://0b0e09d6756b672ac6a8b70895da4fb25090b939578935d4a897497ffaa205e019e068e1ae24ac10d52fa9b8ddb82840d5d990534201a4ad859ee12cb5c91e82@176.9.51.216:30365",

                  // @whilei
                  "enode://158ac5a4817265d0d8b977660b3dbe9abee5694ed212f7091cbf784ddf47623ed015e1cb54594d10c1c46118747ddabe86ebf569cf24ae91f2daa0f1adaae390@159.203.56.33:30303",
                  "enode://efd48ad0879eeb7f9cb5e50f33f7bc21e805a72e90361f145baaa22dd75d111e7cd9c93f1b7060dcb30aa1b3e620269336dbf32339fea4c18925a4c15fe642df@18.205.66.229:30303",
                  "enode://5fbfb426fbb46f8b8c1bd3dd140f5b511da558cd37d60844b525909ab82e13a25ee722293c829e52cb65c2305b1637fa9a2ea4d6634a224d5f400bfe244ac0de@162.243.55.45:30303",
                  "enode://6dd3ac8147fa82e46837ec8c3223d69ac24bcdbab04b036a3705c14f3a02e968f7f1adfcdb002aacec2db46e625c04bf8b5a1f85bb2d40a479b3cc9d45a444af@104.237.131.102:30303",
                  "enode://b9e893ea9cb4537f4fed154233005ae61b441cd0ecd980136138c304fefac194c25a16b73dac05fc66a4198d0c15dd0f33af99b411882c68a019dfa6bb703b9d@18.130.93.66:30303",
                  "enode://3fe9705a02487baea45c1ffebfa4d84819f5f1e68a0dbc18031553242a6a08e39499b61e361a52c2a92f9553efd63763f6fdd34692be0d4ba6823bb2fc346009@178.62.238.75:30303",
                  "enode://d50facc65e46bda6ff594b6e95491efa16e067de41ae96571d9f3cb853d538c44864496fa5e4df10115f02bbbaf47853f932e110a44c89227da7c30e96840596@188.166.163.187:30303",
                  "enode://a0d5c589dc02d008fe4237da9877a5f1daedee0227ab612677eabe323520f003eb5e311af335de9f7964c2092bbc2b3b7ab1cce5a074d8346959f0868b4e366e@46.101.78.44:30303",
                  "enode://c071d96b0c0f13006feae3977fb1b3c2f62caedf643df9a3655bc1b60f777f05e69a4e58bf3547bb299210092764c56df1e08380e91265baa845dca8bc0a71da@68.183.99.5:30303",
                  "enode://83b33409349ffa25e150555f7b4f8deebc68f3d34d782129dc3c8ba07b880c209310a4191e1725f2f6bef59bce9452d821111eaa786deab08a7e6551fca41f4f@206.189.68.191:30303",
                  "enode://0daae2a30f2c73b0b257746587136efb8e3479496f7ea1e943eeb9a663b72dd04582f699f7010ee02c57fc45d1f09568c20a9050ff937f9139e2973ddd98b87b@159.89.169.103:30303",
                  "enode://50808461dd73b3d70537e4c1e5fafd1132b3a90f998399af9205f8889987d62096d4e853813562dd43e7270a71c9d9d4e4dd73a534fdb22fbac98c389c1a7362@178.128.55.119:30303",
                  "enode://5cd218959f8263bc3721d7789070806b0adff1a0ed3f95ec886fb469f9362c7507e3b32b256550b9a7964a23a938e8d42d45a0c34b332bfebc54b29081e83b93@35.187.57.94:30303")
              .map(EnodeURLImpl::fromString)
              .collect(toList()));
  public static final List<EnodeURL> MORDOR_BOOTSTRAP_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://642cf9650dd8869d42525dbf6858012e3b4d64f475e733847ab6f7742341a4397414865d953874e8f5ed91b0e4e1c533dee14ad1d6bb276a5459b2471460ff0d@157.230.152.87:30303", // @meowbits Mordor
                  "enode://651b484b652c07c72adebfaaf8bc2bd95b420b16952ef3de76a9c00ef63f07cca02a20bd2363426f9e6fe372cef96a42b0fec3c747d118f79fd5e02f2a4ebd4e@51.158.190.99:45678", // @q9f Lyrae
                  "enode://859ed8c19ea04eaea41f1cf17c8d2710e2e0affb97328c8392a79f1764118edf2344f1941299f0d676772fa6054447e6f9b3af96444e350b417442bfd7cc832b@34.68.243.226:30303",
                  "enode://9b1bf9613d859ac2071d88509ab40a111b75c1cfc51f4ad78a1fdbb429ff2405de0dc5ea8ae75e6ac88e03e51a465f0b27b517e78517f7220ae163a2e0692991@51.158.190.99:30426", // @q9f Lyrae
                  "enode://534d18fd46c5cd5ba48a68250c47cea27a1376869755ed631c94b91386328039eb607cf10dd8d0aa173f5ec21e3fb45c5d7a7aa904f97bc2557e9cb4ccc703f1@51.158.190.99:30303", // @q9f Lyrae
                  "enode://0d70715514674189792de4ad294b658c96d0ec40fe517fbe9cb7949d3792f25f82357ec77d1bd8bed6ec719ca0c1d608bb34cc702bf3d4bb4507f7280f835452@154.5.137.161:61410",
                  "enode://f50f52b5fe18fd281748905bf5dad5439471f32cc02d99fecf960a983c1f4eba701ffca96afd2f2a68dcf6f97c5d02b566bafce1f361b51717e1a03c1dd9a836@157.230.42.102:30303",
                  "enode://111bd28d5b2c1378d748383fd83ff59572967c317c3063a9f475a26ad3f1517642a164338fb5268d4e32ea1cc48e663bd627dec572f1d201c7198518e5a506b1@88.99.216.30:45834",
                  "enode://07fa944c83597d5e935a2abe6194ed40fc7239e86111c971a43537a33d0184d1cd1b3f1291b8dd3bcfaebfbb802de77c843465a00065b39120c338fdd877ca4a@35.238.126.60:51240",
                  "enode://4ca79bbff7491fed82221259e3f27492e27b95b600594e2f8d5f1fa011123ea267e71873a0db3993e5109845d519d8b849ba2c7e4b48b09bedebb99e1c2ce304@35.238.132.8:30303",
                  "enode://06fdbeb591d26f53b2e7250025fe955ca013431ded930920cf1e3cd1f0c920e9a5e727949d209bc25a07288327b525279b11c5551315c50ff0db483e69fc159b@34.218.225.178:32000",
                  "enode://03b133f731049e3f7be827339c3759be92778c05e54a1847d178c0fdb56fa168aa1e7e61fc77791a7afdd0328a00318f73c01212eb3f3bbe919f5ce8f5b4a314@192.227.105.4:32000",
                  "enode://2592745efd35b4be443b8ee25fd2099de132e037951f9f6d3e8805e0a78f213537f71264b973f1a83a57372f57bbe6acac8d6ae678f39393221c045ccbe3b18c@51.15.116.226:30304",
                  "enode://c0afb552bfe932c72598caa245aef82d55c23555622c5ab946d4e49bb0ab694e46086dcff6793a606527f323ef94d0eb499d01ceb26aefb6fa3f8977105d7dd8@157.230.152.87:52138",
                  "enode://5a1399e6ba3268721dd7656530cd81230dbeb950570c6e3ec2a45effc50c032f66633c5eaaa1a49c51ba1849db37a7bef6e402779ad12dc9943f117e058d7760@35.225.124.17:39306",
                  "enode://15b6ae4e9e18772f297c90d83645b0fbdb56667ce2d747d6d575b21d7b60c2d3cd52b11dec24e418438caf80ddc433232b3685320ed5d0e768e3972596385bfc@51.158.191.43:41235", // @q9f Mizar
                  "enode://5a1399e6ba3268721dd7656530cd81230dbeb950570c6e3ec2a45effc50c032f66633c5eaaa1a49c51ba1849db37a7bef6e402779ad12dc9943f117e058d7760@34.69.121.227:30303",
                  "enode://f840b007500f50c98ea6f9c9e56dabf4690bbbbb7036d43682c531204341aff8315013547e5bee54117eb22bd3603585ae6bf713d9fa710659533fcab65d5b84@34.69.50.155:42078",
                  "enode://b45f008ab8ad73966d0c8c0c923c50f47c0ae50c37a9ea05cc28b00cb94802145a4158412a526fdadd7e539db5eaab72f06a9046a34576ecf5a68efc41ba9d01@34.68.40.145:30303",
                  "enode://1f378945c9b2eeb292d910f461911fd99520a23beda1bc5c8aea12be09e249f8d92615aa3d4d75c938004db5281dabad4a9cf7a0f07ec7c1fc8e7721addc7c85@34.205.41.164:40218",
                  "enode://2b69a3926f36a7748c9021c34050be5e0b64346225e477fe7377070f6289bd363b2be73a06010fd516e6ea3ee90778dd0399bc007bb1281923a79374f842675a@51.15.116.226:30303",
                  "enode://a59e33ccd2b3e52d578f1fbd70c6f9babda2650f0760d6ff3b37742fdcdfdb3defba5d56d315b40c46b70198c7621e63ffa3f987389c7118634b0fefbbdfa7fd@51.158.191.43:38556", // @q9f Mizar
                  "enode://f840b007500f50c98ea6f9c9e56dabf4690bbbbb7036d43682c531204341aff8315013547e5bee54117eb22bd3603585ae6bf713d9fa710659533fcab65d5b84@35.238.101.58:30303",
                  "enode://07fa944c83597d5e935a2abe6194ed40fc7239e86111c971a43537a33d0184d1cd1b3f1291b8dd3bcfaebfbb802de77c843465a00065b39120c338fdd877ca4a@35.238.126.60:30000",
                  "enode://617a2009783a09085ed0d5d5e7250e2e3c142f73448bf28200284bf4825c5926a80f3e9fb481edf38b89ade2aa0ad5a2f14cc935f3150e36e648eddda674fd70@35.225.5.185:51320",
                  "enode://64897cb66fb209548b333467d701e5d205188023d3779411eb2032fd74315723d9bd6e002a7121214fdb68b09173d383d24f92134380fa6f1d2a193030acba1d@91.203.110.229:30303", // @q9f Dubhe
                  "enode://8fa15f5012ac3c47619147220b7772fcc5db0cb7fd132b5d196e7ccacb166ac1fcf83be1dace6cd288e288a85e032423b6e7e9e57f479fe7373edea045caa56b@176.9.51.216:31355", // @q9f Ceibo
                  "enode://34c14141b79652afc334dcd2ba4d8047946246b2310dc8e45737ebe3e6f15f9279ca4702b90bc5be12929f6194e2c3ce19a837b7fec7ebffcee9e9fe4693b504@176.9.51.216:31365" // @q9f Ceibo
                  )
              .map(EnodeURLImpl::fromString)
              .collect(toList()));
}
