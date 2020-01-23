/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.core.coordination.generated;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.6.0-SNAPSHOT.
 */
@SuppressWarnings("rawtypes")
public class CrosschainCoordinationV1 extends Contract {
  private static final String BINARY =
      "60806040523480156200001157600080fd5b50604051620021b0380380620021b0833981810160405260408110156200003757600080fd5b5080516020909101516200005d60008363ffffffff84166001600160e01b036200006516565b50506200024d565b6000838152602081905260409020547401000000000000000000000000000000000000000090046001600160401b0316156200010257604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601860248201527f426c6f636b636861696e20616c72656164792061646465640000000000000000604482015290519081900360640190fd5b6000816001600160401b03161162000166576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040180806020018281038252602b81526020018062002185602b913960400191505060405180910390fd5b6040805184815290517f06b7e169fa5b89e7c3d111aa6a1930c579aa884091b94cba624be47c9de04b1b9181900360200190a16000838152602081815260408083208054600160a01b600160e01b031916740100000000000000000000000000000000000000006001600160401b0396871602176001600160a01b03199081166001600160a01b03979097169690961781556002810180546001818101835591865284862001805433981688179055958452600381018352908320805460ff1916861790559482525291810180546001600160401b03198116908416909201909216179055565b611f28806200025d6000396000f3fe608060405234801561001057600080fd5b506004361061014d5760003560e01c806342cbb15c116100c35780639e1fe0891161007c5780639e1fe0891461053a578063a6db327a146105d0578063b1d50a48146105fc578063d2a3d0a414610676578063dca864e614610693578063e4a4c566146106b05761014d565b806342cbb15c146104005780634dbdfb1b1461040857806359463dbc1461043d578063636edc8914610460578063708f5a6b1461047d57806399740360146104a05761014d565b806317ff78be1161011557806317ff78be1461028f5780631e18d51b14610309578063254b7cd11461032c5780633112818e146103675780633352b8f8146103a757806334940384146103e35761014d565b8063081ca05c146101525780630d8e6e2c1461016c57806310fb72a61461018b578063117368bc146101c457806314ab729f14610250575b600080fd5b61015a6106d9565b60408051918252519081900360200190f35b6101746106de565b6040805161ffff9092168252519081900360200190f35b6101a8600480360360208110156101a157600080fd5b50356106e4565b604080516001600160401b039092168252519081900360200190f35b61024e600480360360a08110156101da57600080fd5b81359161ffff6020820135169160408201359160608101359181019060a081016080820135600160201b81111561021057600080fd5b82018360208201111561022257600080fd5b803590602001918460018302840111600160201b8311171561024357600080fd5b509092509050610706565b005b6102736004803603604081101561026657600080fd5b5080359060200135610a30565b604080516001600160a01b039092168252519081900360200190f35b61024e600480360360608110156102a557600080fd5b813591602081013591810190606081016040820135600160201b8111156102cb57600080fd5b8201836020820111156102dd57600080fd5b803590602001918460018302840111600160201b831117156102fe57600080fd5b509092509050610a6b565b61024e6004803603604081101561031f57600080fd5b5080359060200135610b06565b61024e6004803603606081101561034257600080fd5b5080359060208101356001600160a01b031690604001356001600160401b03166110ae565b6103936004803603604081101561037d57600080fd5b50803590602001356001600160a01b03166110fa565b604080519115158252519081900360200190f35b6103ca600480360360408110156103bd57600080fd5b5080359060200135611127565b6040805163ffffffff9092168252519081900360200190f35b610393600480360360208110156103f957600080fd5b50356111f2565b61015a611216565b61024e6004803603608081101561041e57600080fd5b5080359061ffff6020820135169060408101359060600135151561121a565b61015a6004803603604081101561045357600080fd5b5080359060200135611307565b61015a6004803603602081101561047657600080fd5b5035611337565b61015a6004803603604081101561049357600080fd5b508035906020013561134c565b6104cc600480360360408110156104b657600080fd5b50803590602001356001600160401b031661138a565b604051808481526020018363ffffffff1663ffffffff16815260200180602001828103825283818151815260200191508051906020019060200280838360005b8381101561052457818101518382015260200161050c565b5050505090500194505050505060405180910390f35b61024e600480360360c081101561055057600080fd5b8135916020810135916040820135916060810135916001600160401b03608083013516919081019060c0810160a0820135600160201b81111561059257600080fd5b8201836020820111156105a457600080fd5b803590602001918460018302840111600160201b831117156105c557600080fd5b509092509050611418565b610393600480360360408110156105e657600080fd5b50803590602001356001600160401b03166115e9565b61024e6004803603606081101561061257600080fd5b813591602081013591810190606081016040820135600160201b81111561063857600080fd5b82018360208201111561064a57600080fd5b803590602001918460018302840111600160201b8311171561066b57600080fd5b509092509050611615565b6104cc6004803603602081101561068c57600080fd5b50356116a8565b61015a600480360360208110156106a957600080fd5b50356116e0565b61024e600480360360608110156106c657600080fd5b50803590602081013590604001356116f5565b600081565b60015b90565b600090815260208190526040902054600160a01b90046001600160401b031690565b600086815260208181526040808320338452600301909152902054869060ff1661072f57600080fd5b60008661ffff16600581111561074157fe5b9050600080898152602081815260408083208a845260060190915290205460ff16600581111561076d57fe5b1461077757600080fd5b600181600581111561078557fe5b14156107b35760008881526020818152604080832089845260050190915290205460ff16156107b357600080fd5b60028160058111156107c157fe5b14156108295760008881526020818152604080832089845260050190915290205460ff1615156001146107f357600080fd5b856000808a8152602001908152602001600020600401868154811061081457fe5b90600052602060002001541461082957600080fd5b600381600581111561083757fe5b141561086e576000888152602081815260408083206001600160a01b038a16845260030190915290205460ff161561086e57600080fd5b600481600581111561087c57fe5b141561091a576000888152602081815260408083206001600160a01b038a168452600301909152902054869060ff1615156001146108b957600080fd5b6001600160a01b0381163314156108cf57600080fd5b806001600160a01b03166000808b815260200190815260200160002060020187815481106108f957fe5b6000918252602090912001546001600160a01b03161461091857600080fd5b505b600581600581111561092857fe5b1415610996576000888152602081905260409020600701546001600160401b0316851161095457600080fd5b61099384848080601f01602080910402602001604051908101604052809392919081815260200183838082843760009201919091525061188592505050565b50505b6000888152602081815260408083208984526006019091529020805482919060ff191660018360058111156109c757fe5b021790555060008881526020818152604080832080548a85526006909101909252909120600160a01b9091046001600160401b03164301600182015560028101869055610a18906003018585611d42565b50610a26888888600161191a565b5050505050505050565b6000828152602081905260408120600201805483908110610a4d57fe5b6000918252602090912001546001600160a01b031690505b92915050565b60408051602080820187905281830186905282518083038401815260609092019092528051910120600160008281526001602052604090205460ff166003811115610ab257fe5b14610abc57600080fd5b60008181526001602081905260409091200154431115610adb57600080fd5b6000818152600160208190526040909120805460039260ff1990911690835b02179055505050505050565b600082815260208181526040808320338452600301909152902054829060ff16610b2f57600080fd5b60008381526020818152604080832085845260060190915281205460ff1690816005811115610b5a57fe5b1415610b6557600080fd5b6000848152602081815260408083208684526006019091529020600101544311610b8e57600080fd5b600084815260208181526040808320805460018201548886526006909201845282852060050154835163a81ce84760e01b81526001600160401b0393841660048201528382166024820152600160401b909104909216604483015291516001600160a01b039092169392849263a81ce847926064808201939291829003018186803b158015610c1c57600080fd5b505afa158015610c30573d6000803e3d6000fd5b505050506040513d6020811015610c4657600080fd5b505190507faeb5b7640625260f6f8914a13e4cd86b256b00761645b6466e94ccb8f02d0fd486846005811115610c7857fe5b6040805192835261ffff90911660208301528181018890528315156060830152519081900360800190a18015610f8c57600086815260208181526040808320888452600601909152902060020154856003856005811115610cd557fe5b1415610d6157600088815260208181526040808320600281018054600180820183559186528486200180546001600160a01b0319166001600160a01b0388169081179091558552600382018452918420805460ff1916831790558b845292909152908101805467ffffffffffffffff1981166001600160401b0391821690930116919091179055610f89565b6001856005811115610d6f57fe5b1415610db95760008881526020818152604080832060048101805460018181018355918652848620018c90558b85526005909101909252909120805460ff19169091179055610f89565b6004856005811115610dc757fe5b1415610e5f576000888152602081905260409020600201805483908110610dea57fe5b600091825260208083209190910180546001600160a01b031916905589825281815260408083206001600160a01b0385168452600381018352908320805460ff191690558a8352919052600101805467ffffffffffffffff1981166001600160401b0391821660001901909116179055610f89565b6002856005811115610e6d57fe5b1415610ec3576000888152602081905260409020600401805483908110610e9057fe5b6000918252602080832090910182905589825281815260408083208a84526005019091529020805460ff19169055610f89565b6005856005811115610ed157fe5b1415610f89576000888152602081815260408083208a84526006018252918290206002808201546003909201805485516001821615610100026000190190911692909204601f8101859004850283018501909552848252610f89948d94830182828015610f7f5780601f10610f5457610100808354040283529160200191610f7f565b820191906000526020600020905b815481529060010190602001808311610f6257829003601f168201915b5050505050611a36565b50505b60005b60008781526020819052604090206002015481101561104c576000878152602081905260408120600201805483908110610fc557fe5b6000918252602090912001546001600160a01b031614611044576000878152602081815260408083208984526006810183529083208a8452918390526002018054600490920192918490811061101757fe5b60009182526020808320909101546001600160a01b031683528201929092526040019020805460ff191690555b600101610f8f565b506000868152602081815260408083208884526006019091528120805460ff1916815560018101829055600281018290559061108b6003830182611dc0565b5060050180546fffffffffffffffffffffffffffffffff19169055505050505050565b3360009081527fad3228b676f7d3cd4284a5443f17f1962b36e491b30a40b2405849e597ba5fb8602052604081205460ff166110e957600080fd5b6110f4848484611b6a565b50505050565b6000828152602081815260408083206001600160a01b038516845260030190915290205460ff1692915050565b60408051602080820185905281830184905282518083038401815260609092019092528051910120600090600260008281526001602052604090205460ff16600381111561117157fe5b14156111825760025b915050610a65565b60008181526001602052604081205460ff16600381111561119f57fe5b14156111ac57600061117a565b600081815260016020819052604090912001544311156111cd57600361117a565b60008181526001602052604090205460ff1660038111156111ea57fe5b949350505050565b600090815260208190526040902054600160a01b90046001600160401b0316151590565b4390565b600084815260208181526040808320338452600301909152902054849060ff1661124357600080fd5b60008461ffff16600581111561125557fe5b905080600581111561126357fe5b60008781526020818152604080832088845260060190915290205460ff16600581111561128c57fe5b1461129657600080fd5b600086815260208181526040808320878452600601825280832033845260040190915290205460ff16156112c957600080fd5b6000868152602081815260408083208784526006019091529020600101544311156112f357600080fd5b6112ff8686868661191a565b505050505050565b600082815260208190526040812060040180548390811061132457fe5b9060005260206000200154905092915050565b60009081526020819052604090206004015490565b604080516020808201949094528082019290925280518083038201815260609092018152815191830191909120600090815260019283905220015490565b6000828152602081815260408083206001600160401b03851684526008018252918290208054600182015460029092018054855181860281018601909652808652919463ffffffff90931693606093929083018282801561140a57602002820191906000526020600020905b8154815260200190600101908083116113f6575b505050505090509250925092565b6040805160208082018a9052818301899052825180830384018152606090920190925280519101206000808281526001602052604090205460ff16600381111561145e57fe5b1461146857600080fd5b43851161147457600080fd5b600061147e611cfb565b60408051600160208083019190915281830184905230606090811b90830152607482018d9052609482018c905260b482018b905260d48083018b90528351808403909101815260f483019384905281845280516101148401528051949550937f51626fa7934ba5178af5a4277c29c750c6ff0885a1d2ea7995ce18d8cb69940a93859390928392610134909201919085019080838360005b8381101561152e578181015183820152602001611516565b50505050905090810190601f16801561155b5780820380516001836020036101000a031916815260200191505b509250505060405180910390a1600083815260016020818152604092839020805460ff19168317815590910189905581518c81529081018b90528082018a9052606081018990526001600160401b038816608082015290517fe33b31a9269807638ad52eca8f427d1c46f3115f94329634c0753bdc529c4f9c9181900360a00190a150505050505050505050565b6000828152602081815260408083206001600160401b0385168452600801909152902054151592915050565b60408051602080820187905281830186905282518083038401815260609092019092528051910120600160008281526001602052604090205460ff16600381111561165c57fe5b1461166657600080fd5b6000818152600160208190526040909120015443111561168557600080fd5b6000818152600160208190526040909120805460029260ff199091169083610afa565b60008181526020819052604081206007015481906060906116d39085906001600160401b031661138a565b9250925092509193909250565b60009081526020819052604090206002015490565b600083815260208190526040812060040180548490811061171257fe5b60009182526020918290200154604080513360601b818501526034808201879052825180830390910181526054909101909152805192019190912090915080821461175c57600080fd5b60008581526020818152604080832033845260030190915290205460ff16611832576040805186815233602082015281517f340f3af47c95ed6ef6db88a578a410e987399279edcda9b5dc2c393a5d3b9d55929181900390910190a160008581526020818152604080832060028101805460018082018355918652848620018054336001600160a01b031990911681179091558552600382018452918420805460ff19168317905588845292909152908101805467ffffffffffffffff1981166001600160401b03918216909301169190911790555b600085815260208190526040902060040180548590811061184f57fe5b600091825260208083209091018290559581528086526040808220938252600590930190955250909220805460ff191690555050565b80516000908152815160609082906084146118d15760405162461bcd60e51b8152600401808060200182810382526023815260200180611ed16023913960400191505060405180910390fd5b60408051600480825260a08201909252906020820160808038833901905050915060245b600460208451020181116119135784810151838201526020016118f5565b5050915091565b6040805185815233602082015261ffff85168183015260608101849052821515608082015290517fef19f8b95a20f38d0a8a745702c10e0ef86b204ddcef51d7fba0b0da2d8aebf29181900360a00190a160008481526020818152604080832085845260060182528083203384526004019091529020805460ff1916600117905580156119e457600084815260208181526040808320858452600601909152902060050180546001600160401b038082166001011667ffffffffffffffff199091161790556110f4565b6000848152602081815260408083208584526006019091529020600501805460016001600160401b03600160401b808404821692909201160267ffffffffffffffff60401b1990911617905550505050565b6000838152602081815260408083206001600160401b038616845260080190915290205415611a965760405162461bcd60e51b815260040180806020018281038252602b815260200180611ea6602b913960400191505060405180910390fd5b6000838152602081815260408083206001600160401b038616845260080190915281204390556060611ac783611885565b6000878152602081815260408083206001600160401b038a168452600801825290912060018101805463ffffffff191663ffffffff86161790558251939550919350611b1c9260029092019190840190611e07565b50505060009283525060208290526040909120600701805467ffffffffffffffff60401b1981166001600160401b03918216600160401b021767ffffffffffffffff19169216919091179055565b600083815260208190526040902054600160a01b90046001600160401b031615611bdb576040805162461bcd60e51b815260206004820152601860248201527f426c6f636b636861696e20616c72656164792061646465640000000000000000604482015290519081900360640190fd5b6000816001600160401b031611611c235760405162461bcd60e51b815260040180806020018281038252602b815260200180611e7b602b913960400191505060405180910390fd5b6040805184815290517f06b7e169fa5b89e7c3d111aa6a1930c579aa884091b94cba624be47c9de04b1b9181900360200190a1600083815260208181526040808320805467ffffffffffffffff60a01b1916600160a01b6001600160401b0396871602176001600160a01b03199081166001600160a01b03979097169690961781556002810180546001818101835591865284862001805433981688179055958452600381018352908320805460ff19168617905594825252918101805467ffffffffffffffff198116908416909201909216179055565b6000611d076001611d0c565b905090565b60006020611d18611e42565b838152611d23611e42565b6020808285856078600019fa611d3857600080fd5b5051949350505050565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f10611d835782800160ff19823516178555611db0565b82800160010185558215611db0579182015b82811115611db0578235825591602001919060010190611d95565b50611dbc929150611e60565b5090565b50805460018160011615610100020316600290046000825580601f10611de65750611e04565b601f016020900490600052602060002090810190611e049190611e60565b50565b828054828255906000526020600020908101928215611db0579160200282015b82811115611db0578251825591602001919060010190611e27565b60405180602001604052806001906020820280388339509192915050565b6106e191905b80821115611dbc5760008155600101611e6656fe54686520766f74696e6720706572696f64206d7573742062652067726561746572207468616e207a65726f5075626c6963204b65792065786973747320666f7220746865207370656369666965642076657273696f6e5075626c6963206b65792077726f6e672073697a6520666f7220616c676f726974686da265627a7a72305820b69e3877c7c0b616bffb65c159749a3b6a4027cedb6e173f910b395bd2c9bf4e64736f6c634300050a003254686520766f74696e6720706572696f64206d7573742062652067726561746572207468616e207a65726f";

  public static final String FUNC_MANAGEMENT_PSEUDO_BLOCKCHAIN_ID =
      "MANAGEMENT_PSEUDO_BLOCKCHAIN_ID";

  public static final String FUNC_GETVERSION = "getVersion";

  public static final String FUNC_GETVOTINGPERIOD = "getVotingPeriod";

  public static final String FUNC_PROPOSEVOTE = "proposeVote";

  public static final String FUNC_GETUNMASKEDBLOCKCHAINPARTICIPANT =
      "getUnmaskedBlockchainParticipant";

  public static final String FUNC_IGNORE = "ignore";

  public static final String FUNC_ACTIONVOTES = "actionVotes";

  public static final String FUNC_ADDBLOCKCHAIN = "addBlockchain";

  public static final String FUNC_ISUNMASKEDBLOCKCHAINPARTICIPANT =
      "isUnmaskedBlockchainParticipant";

  public static final String FUNC_GETCROSSCHAINTRANSACTIONSTATUS = "getCrosschainTransactionStatus";

  public static final String FUNC_GETBLOCKCHAINEXISTS = "getBlockchainExists";

  public static final String FUNC_GETBLOCKNUMBER = "getBlockNumber";

  public static final String FUNC_VOTE = "vote";

  public static final String FUNC_GETMASKEDBLOCKCHAINPARTICIPANT = "getMaskedBlockchainParticipant";

  public static final String FUNC_GETMASKEDBLOCKCHAINPARTICIPANTSSIZE =
      "getMaskedBlockchainParticipantsSize";

  public static final String FUNC_GETCROSSCHAINTRANSACTIONTIMEOUT =
      "getCrosschainTransactionTimeout";

  public static final String FUNC_GETPUBLICKEY = "getPublicKey";

  public static final String FUNC_START = "start";

  public static final String FUNC_PUBLICKEYEXISTS = "publicKeyExists";

  public static final String FUNC_COMMIT = "commit";

  public static final String FUNC_GETACTIVEPUBLICKEY = "getActivePublicKey";

  public static final String FUNC_GETUNMASKEDBLOCKCHAINPARTICIPANTSSIZE =
      "getUnmaskedBlockchainParticipantsSize";

  public static final String FUNC_UNMASK = "unmask";

  public static final Event ADDEDBLOCKCHAIN_EVENT =
      new Event(
          "AddedBlockchain", Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));;

  public static final Event ADDINGBLOCKCHAINMASKEDPARTICIPANT_EVENT =
      new Event(
          "AddingBlockchainMaskedParticipant",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}));;

  public static final Event ADDINGBLOCKCHAINUNMASKEDPARTICIPANT_EVENT =
      new Event(
          "AddingBlockchainUnmaskedParticipant",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {}, new TypeReference<Address>() {}));;

  public static final Event PARTICIPANTVOTED_EVENT =
      new Event(
          "ParticipantVoted",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {},
              new TypeReference<Address>() {},
              new TypeReference<Uint16>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Bool>() {}));;

  public static final Event VOTERESULT_EVENT =
      new Event(
          "VoteResult",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {},
              new TypeReference<Uint16>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Bool>() {}));;

  public static final Event START_EVENT =
      new Event(
          "Start",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Uint64>() {}));;

  public static final Event DUMP1_EVENT =
      new Event(
          "Dump1",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Address>() {}));;

  public static final Event DUMP2_EVENT =
      new Event(
          "Dump2",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Uint256>() {}));;

  public static final Event DUMP3_EVENT =
      new Event("Dump3", Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));;

  public static final Event ALG_EVENT =
      new Event("Alg", Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));;

  @Deprecated
  protected CrosschainCoordinationV1(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected CrosschainCoordinationV1(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected CrosschainCoordinationV1(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected CrosschainCoordinationV1(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<BigInteger> MANAGEMENT_PSEUDO_BLOCKCHAIN_ID() {
    final Function function =
        new Function(
            FUNC_MANAGEMENT_PSEUDO_BLOCKCHAIN_ID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<BigInteger> getVersion() {
    final Function function =
        new Function(
            FUNC_GETVERSION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint16>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<BigInteger> getVotingPeriod(BigInteger _blockchainId) {
    final Function function =
        new Function(
            FUNC_GETVOTINGPERIOD,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_blockchainId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint64>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> proposeVote(
      BigInteger _blockchainId,
      BigInteger _action,
      BigInteger _voteTarget,
      BigInteger _additionalInfo1,
      byte[] _additionalInfo2) {
    final Function function =
        new Function(
            FUNC_PROPOSEVOTE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint16(_action),
                new org.web3j.abi.datatypes.generated.Uint256(_voteTarget),
                new org.web3j.abi.datatypes.generated.Uint256(_additionalInfo1),
                new org.web3j.abi.datatypes.DynamicBytes(_additionalInfo2)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<String> getUnmaskedBlockchainParticipant(
      BigInteger _blockchainId, BigInteger _index) {
    final Function function =
        new Function(
            FUNC_GETUNMASKEDBLOCKCHAINPARTICIPANT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_index)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteFunctionCall<TransactionReceipt> ignore(
      BigInteger _originatingBlockchainId, BigInteger _crosschainTransactionId, byte[] param2) {
    final Function function =
        new Function(
            FUNC_IGNORE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_originatingBlockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_crosschainTransactionId),
                new org.web3j.abi.datatypes.DynamicBytes(param2)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> actionVotes(
      BigInteger _blockchainId, BigInteger _voteTarget) {
    final Function function =
        new Function(
            FUNC_ACTIONVOTES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_voteTarget)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> addBlockchain(
      BigInteger _blockchainId, String _votingAlgorithmContract, BigInteger _votingPeriod) {
    final Function function =
        new Function(
            FUNC_ADDBLOCKCHAIN,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.Address(160, _votingAlgorithmContract),
                new org.web3j.abi.datatypes.generated.Uint64(_votingPeriod)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<Boolean> isUnmaskedBlockchainParticipant(
      BigInteger _blockchainId, String _participant) {
    final Function function =
        new Function(
            FUNC_ISUNMASKEDBLOCKCHAINPARTICIPANT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.Address(160, _participant)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  public RemoteFunctionCall<BigInteger> getCrosschainTransactionStatus(
      BigInteger _originatingBlockchainId, BigInteger _crosschainTransactionId) {
    final Function function =
        new Function(
            FUNC_GETCROSSCHAINTRANSACTIONSTATUS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_originatingBlockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_crosschainTransactionId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<Boolean> getBlockchainExists(BigInteger _blockchainId) {
    final Function function =
        new Function(
            FUNC_GETBLOCKCHAINEXISTS,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_blockchainId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  public RemoteFunctionCall<BigInteger> getBlockNumber() {
    final Function function =
        new Function(
            FUNC_GETBLOCKNUMBER,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> vote(
      BigInteger _blockchainId, BigInteger _action, BigInteger _voteTarget, Boolean _voteFor) {
    final Function function =
        new Function(
            FUNC_VOTE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint16(_action),
                new org.web3j.abi.datatypes.generated.Uint256(_voteTarget),
                new org.web3j.abi.datatypes.Bool(_voteFor)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<BigInteger> getMaskedBlockchainParticipant(
      BigInteger _blockchainId, BigInteger _index) {
    final Function function =
        new Function(
            FUNC_GETMASKEDBLOCKCHAINPARTICIPANT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_index)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<BigInteger> getMaskedBlockchainParticipantsSize(
      BigInteger _blockchainId) {
    final Function function =
        new Function(
            FUNC_GETMASKEDBLOCKCHAINPARTICIPANTSSIZE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_blockchainId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<BigInteger> getCrosschainTransactionTimeout(
      BigInteger _originatingBlockchainId, BigInteger _crosschainTransactionId) {
    final Function function =
        new Function(
            FUNC_GETCROSSCHAINTRANSACTIONTIMEOUT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_originatingBlockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_crosschainTransactionId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  //  public RemoteFunctionCall<Tuple3<BigInteger, BigInteger, List<BigInteger>>> getPublicKey(
  //      BigInteger _blockchainId, BigInteger _keyVersion) {
  //    final Function function =
  //        new Function(
  //            FUNC_GETPUBLICKEY,
  //            Arrays.<Type>asList(
  //                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
  //                new org.web3j.abi.datatypes.generated.Uint64(_keyVersion)),
  //            Arrays.<TypeReference<?>>asList(
  //                new TypeReference<Uint256>() {},
  //                new TypeReference<Uint32>() {},
  //                new TypeReference<DynamicArray<Uint256>>() {}));
  //    return new RemoteFunctionCall<Tuple3<BigInteger, BigInteger, List<BigInteger>>>(
  //        function,
  //        new Callable<Tuple3<BigInteger, BigInteger, List<BigInteger>>>() {
  //          @Override
  //          public Tuple3<BigInteger, BigInteger, List<BigInteger>> call() throws Exception {
  //            List<Type> results = executeCallMultipleValueReturn(function);
  //            return new Tuple3<BigInteger, BigInteger, List<BigInteger>>(
  //                (BigInteger) results.get(0).getValue(),
  //                (BigInteger) results.get(1).getValue(),
  //                convertToNative((List<Uint256>) results.get(2).getValue()));
  //          }
  //        });
  //  }

  public RemoteFunctionCall<TransactionReceipt> start(
      BigInteger _originatingBlockchainId,
      BigInteger _crosschainTransactionId,
      BigInteger _hashOfMessage,
      BigInteger _transactionTimeoutBlock,
      BigInteger _keyVersion,
      byte[] param5) {
    final Function function =
        new Function(
            FUNC_START,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_originatingBlockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_crosschainTransactionId),
                new org.web3j.abi.datatypes.generated.Uint256(_hashOfMessage),
                new org.web3j.abi.datatypes.generated.Uint256(_transactionTimeoutBlock),
                new org.web3j.abi.datatypes.generated.Uint64(_keyVersion),
                new org.web3j.abi.datatypes.DynamicBytes(param5)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<Boolean> publicKeyExists(
      BigInteger _blockchainId, BigInteger _keyVersion) {
    final Function function =
        new Function(
            FUNC_PUBLICKEYEXISTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint64(_keyVersion)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  public RemoteFunctionCall<TransactionReceipt> commit(
      BigInteger _originatingBlockchainId, BigInteger _crosschainTransactionId, byte[] param2) {
    final Function function =
        new Function(
            FUNC_COMMIT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_originatingBlockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_crosschainTransactionId),
                new org.web3j.abi.datatypes.DynamicBytes(param2)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  //  public RemoteFunctionCall<Tuple3<BigInteger, BigInteger, List<BigInteger>>>
  // getActivePublicKey(
  //      BigInteger _blockchainId) {
  //    final Function function =
  //        new Function(
  //            FUNC_GETACTIVEPUBLICKEY,
  //            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_blockchainId)),
  //            Arrays.<TypeReference<?>>asList(
  //                new TypeReference<Uint256>() {},
  //                new TypeReference<Uint32>() {},
  //                new TypeReference<DynamicArray<Uint256>>() {}));
  //    return new RemoteFunctionCall<Tuple3<BigInteger, BigInteger, List<BigInteger>>>(
  //        function,
  //        new Callable<Tuple3<BigInteger, BigInteger, List<BigInteger>>>() {
  //          @Override
  //          public Tuple3<BigInteger, BigInteger, List<BigInteger>> call() throws Exception {
  //            List<Type> results = executeCallMultipleValueReturn(function);
  //            return new Tuple3<BigInteger, BigInteger, List<BigInteger>>(
  //                (BigInteger) results.get(0).getValue(),
  //                (BigInteger) results.get(1).getValue(),
  //                convertToNative((List<Uint256>) results.get(2).getValue()));
  //          }
  //        });
  //  }

  public RemoteFunctionCall<BigInteger> getUnmaskedBlockchainParticipantsSize(
      BigInteger _blockchainId) {
    final Function function =
        new Function(
            FUNC_GETUNMASKEDBLOCKCHAINPARTICIPANTSSIZE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_blockchainId)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> unmask(
      BigInteger _blockchainId, BigInteger _index, BigInteger _salt) {
    final Function function =
        new Function(
            FUNC_UNMASK,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_blockchainId),
                new org.web3j.abi.datatypes.generated.Uint256(_index),
                new org.web3j.abi.datatypes.generated.Uint256(_salt)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public List<AddedBlockchainEventResponse> getAddedBlockchainEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ADDEDBLOCKCHAIN_EVENT, transactionReceipt);
    ArrayList<AddedBlockchainEventResponse> responses =
        new ArrayList<AddedBlockchainEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AddedBlockchainEventResponse typedResponse = new AddedBlockchainEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._blockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AddedBlockchainEventResponse> addedBlockchainEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, AddedBlockchainEventResponse>() {
              @Override
              public AddedBlockchainEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ADDEDBLOCKCHAIN_EVENT, log);
                AddedBlockchainEventResponse typedResponse = new AddedBlockchainEventResponse();
                typedResponse.log = log;
                typedResponse._blockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AddedBlockchainEventResponse> addedBlockchainEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ADDEDBLOCKCHAIN_EVENT));
    return addedBlockchainEventFlowable(filter);
  }

  public List<AddingBlockchainMaskedParticipantEventResponse>
      getAddingBlockchainMaskedParticipantEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ADDINGBLOCKCHAINMASKEDPARTICIPANT_EVENT, transactionReceipt);
    ArrayList<AddingBlockchainMaskedParticipantEventResponse> responses =
        new ArrayList<AddingBlockchainMaskedParticipantEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AddingBlockchainMaskedParticipantEventResponse typedResponse =
          new AddingBlockchainMaskedParticipantEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._blockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._participant = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AddingBlockchainMaskedParticipantEventResponse>
      addingBlockchainMaskedParticipantEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<
                Log, AddingBlockchainMaskedParticipantEventResponse>() {
              @Override
              public AddingBlockchainMaskedParticipantEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ADDINGBLOCKCHAINMASKEDPARTICIPANT_EVENT, log);
                AddingBlockchainMaskedParticipantEventResponse typedResponse =
                    new AddingBlockchainMaskedParticipantEventResponse();
                typedResponse.log = log;
                typedResponse._blockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._participant =
                    (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AddingBlockchainMaskedParticipantEventResponse>
      addingBlockchainMaskedParticipantEventFlowable(
          DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ADDINGBLOCKCHAINMASKEDPARTICIPANT_EVENT));
    return addingBlockchainMaskedParticipantEventFlowable(filter);
  }

  public List<AddingBlockchainUnmaskedParticipantEventResponse>
      getAddingBlockchainUnmaskedParticipantEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(
            ADDINGBLOCKCHAINUNMASKEDPARTICIPANT_EVENT, transactionReceipt);
    ArrayList<AddingBlockchainUnmaskedParticipantEventResponse> responses =
        new ArrayList<AddingBlockchainUnmaskedParticipantEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AddingBlockchainUnmaskedParticipantEventResponse typedResponse =
          new AddingBlockchainUnmaskedParticipantEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._blockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._participant = (String) eventValues.getNonIndexedValues().get(1).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AddingBlockchainUnmaskedParticipantEventResponse>
      addingBlockchainUnmaskedParticipantEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<
                Log, AddingBlockchainUnmaskedParticipantEventResponse>() {
              @Override
              public AddingBlockchainUnmaskedParticipantEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ADDINGBLOCKCHAINUNMASKEDPARTICIPANT_EVENT, log);
                AddingBlockchainUnmaskedParticipantEventResponse typedResponse =
                    new AddingBlockchainUnmaskedParticipantEventResponse();
                typedResponse.log = log;
                typedResponse._blockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._participant =
                    (String) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AddingBlockchainUnmaskedParticipantEventResponse>
      addingBlockchainUnmaskedParticipantEventFlowable(
          DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ADDINGBLOCKCHAINUNMASKEDPARTICIPANT_EVENT));
    return addingBlockchainUnmaskedParticipantEventFlowable(filter);
  }

  public List<ParticipantVotedEventResponse> getParticipantVotedEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(PARTICIPANTVOTED_EVENT, transactionReceipt);
    ArrayList<ParticipantVotedEventResponse> responses =
        new ArrayList<ParticipantVotedEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      ParticipantVotedEventResponse typedResponse = new ParticipantVotedEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._blockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._participant = (String) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse._action = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse._voteTarget = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
      typedResponse._votedFor = (Boolean) eventValues.getNonIndexedValues().get(4).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<ParticipantVotedEventResponse> participantVotedEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, ParticipantVotedEventResponse>() {
              @Override
              public ParticipantVotedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(PARTICIPANTVOTED_EVENT, log);
                ParticipantVotedEventResponse typedResponse = new ParticipantVotedEventResponse();
                typedResponse.log = log;
                typedResponse._blockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._participant =
                    (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse._action =
                    (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse._voteTarget =
                    (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                typedResponse._votedFor =
                    (Boolean) eventValues.getNonIndexedValues().get(4).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<ParticipantVotedEventResponse> participantVotedEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(PARTICIPANTVOTED_EVENT));
    return participantVotedEventFlowable(filter);
  }

  public List<VoteResultEventResponse> getVoteResultEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(VOTERESULT_EVENT, transactionReceipt);
    ArrayList<VoteResultEventResponse> responses =
        new ArrayList<VoteResultEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      VoteResultEventResponse typedResponse = new VoteResultEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._blockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._action = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse._voteTarget = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse._result = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<VoteResultEventResponse> voteResultEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, VoteResultEventResponse>() {
              @Override
              public VoteResultEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(VOTERESULT_EVENT, log);
                VoteResultEventResponse typedResponse = new VoteResultEventResponse();
                typedResponse.log = log;
                typedResponse._blockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._action =
                    (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse._voteTarget =
                    (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse._result =
                    (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<VoteResultEventResponse> voteResultEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(VOTERESULT_EVENT));
    return voteResultEventFlowable(filter);
  }

  public List<StartEventResponse> getStartEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(START_EVENT, transactionReceipt);
    ArrayList<StartEventResponse> responses = new ArrayList<StartEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      StartEventResponse typedResponse = new StartEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._originatingBlockchainId =
          (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._crosschainTransactionId =
          (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse._hashOfMessage =
          (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse._transactionTimeoutBlock =
          (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
      typedResponse._keyVersion = (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<StartEventResponse> startEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, StartEventResponse>() {
              @Override
              public StartEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(START_EVENT, log);
                StartEventResponse typedResponse = new StartEventResponse();
                typedResponse.log = log;
                typedResponse._originatingBlockchainId =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._crosschainTransactionId =
                    (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse._hashOfMessage =
                    (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse._transactionTimeoutBlock =
                    (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                typedResponse._keyVersion =
                    (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<StartEventResponse> startEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(START_EVENT));
    return startEventFlowable(filter);
  }

  public List<Dump1EventResponse> getDump1Events(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DUMP1_EVENT, transactionReceipt);
    ArrayList<Dump1EventResponse> responses = new ArrayList<Dump1EventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      Dump1EventResponse typedResponse = new Dump1EventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.a = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.b = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.c = (String) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<Dump1EventResponse> dump1EventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, Dump1EventResponse>() {
              @Override
              public Dump1EventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(DUMP1_EVENT, log);
                Dump1EventResponse typedResponse = new Dump1EventResponse();
                typedResponse.log = log;
                typedResponse.a = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.b = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.c = (String) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<Dump1EventResponse> dump1EventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DUMP1_EVENT));
    return dump1EventFlowable(filter);
  }

  public List<Dump2EventResponse> getDump2Events(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DUMP2_EVENT, transactionReceipt);
    ArrayList<Dump2EventResponse> responses = new ArrayList<Dump2EventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      Dump2EventResponse typedResponse = new Dump2EventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.a = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.b = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.c = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse.d = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<Dump2EventResponse> dump2EventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, Dump2EventResponse>() {
              @Override
              public Dump2EventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(DUMP2_EVENT, log);
                Dump2EventResponse typedResponse = new Dump2EventResponse();
                typedResponse.log = log;
                typedResponse.a = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.b = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.c = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.d = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<Dump2EventResponse> dump2EventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DUMP2_EVENT));
    return dump2EventFlowable(filter);
  }

  public List<Dump3EventResponse> getDump3Events(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DUMP3_EVENT, transactionReceipt);
    ArrayList<Dump3EventResponse> responses = new ArrayList<Dump3EventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      Dump3EventResponse typedResponse = new Dump3EventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.a = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<Dump3EventResponse> dump3EventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, Dump3EventResponse>() {
              @Override
              public Dump3EventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(DUMP3_EVENT, log);
                Dump3EventResponse typedResponse = new Dump3EventResponse();
                typedResponse.log = log;
                typedResponse.a = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<Dump3EventResponse> dump3EventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DUMP3_EVENT));
    return dump3EventFlowable(filter);
  }

  public List<AlgEventResponse> getAlgEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ALG_EVENT, transactionReceipt);
    ArrayList<AlgEventResponse> responses = new ArrayList<AlgEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AlgEventResponse typedResponse = new AlgEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.alg = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AlgEventResponse> algEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, AlgEventResponse>() {
              @Override
              public AlgEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ALG_EVENT, log);
                AlgEventResponse typedResponse = new AlgEventResponse();
                typedResponse.log = log;
                typedResponse.alg =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AlgEventResponse> algEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ALG_EVENT));
    return algEventFlowable(filter);
  }

  @Deprecated
  public static CrosschainCoordinationV1 load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new CrosschainCoordinationV1(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static CrosschainCoordinationV1 load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new CrosschainCoordinationV1(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static CrosschainCoordinationV1 load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new CrosschainCoordinationV1(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static CrosschainCoordinationV1 load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new CrosschainCoordinationV1(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<CrosschainCoordinationV1> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _votingAlg,
      BigInteger _votingPeriod) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(160, _votingAlg),
                new org.web3j.abi.datatypes.generated.Uint32(_votingPeriod)));
    return deployRemoteCall(
        CrosschainCoordinationV1.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  public static RemoteCall<CrosschainCoordinationV1> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _votingAlg,
      BigInteger _votingPeriod) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(160, _votingAlg),
                new org.web3j.abi.datatypes.generated.Uint32(_votingPeriod)));
    return deployRemoteCall(
        CrosschainCoordinationV1.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<CrosschainCoordinationV1> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _votingAlg,
      BigInteger _votingPeriod) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(160, _votingAlg),
                new org.web3j.abi.datatypes.generated.Uint32(_votingPeriod)));
    return deployRemoteCall(
        CrosschainCoordinationV1.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<CrosschainCoordinationV1> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _votingAlg,
      BigInteger _votingPeriod) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(160, _votingAlg),
                new org.web3j.abi.datatypes.generated.Uint32(_votingPeriod)));
    return deployRemoteCall(
        CrosschainCoordinationV1.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  public static class AddedBlockchainEventResponse extends BaseEventResponse {
    public BigInteger _blockchainId;
  }

  public static class AddingBlockchainMaskedParticipantEventResponse extends BaseEventResponse {
    public BigInteger _blockchainId;

    public byte[] _participant;
  }

  public static class AddingBlockchainUnmaskedParticipantEventResponse extends BaseEventResponse {
    public BigInteger _blockchainId;

    public String _participant;
  }

  public static class ParticipantVotedEventResponse extends BaseEventResponse {
    public BigInteger _blockchainId;

    public String _participant;

    public BigInteger _action;

    public BigInteger _voteTarget;

    public Boolean _votedFor;
  }

  public static class VoteResultEventResponse extends BaseEventResponse {
    public BigInteger _blockchainId;

    public BigInteger _action;

    public BigInteger _voteTarget;

    public Boolean _result;
  }

  public static class StartEventResponse extends BaseEventResponse {
    public BigInteger _originatingBlockchainId;

    public BigInteger _crosschainTransactionId;

    public BigInteger _hashOfMessage;

    public BigInteger _transactionTimeoutBlock;

    public BigInteger _keyVersion;
  }

  public static class Dump1EventResponse extends BaseEventResponse {
    public BigInteger a;

    public BigInteger b;

    public String c;
  }

  public static class Dump2EventResponse extends BaseEventResponse {
    public BigInteger a;

    public BigInteger b;

    public BigInteger c;

    public BigInteger d;
  }

  public static class Dump3EventResponse extends BaseEventResponse {
    public byte[] a;
  }

  public static class AlgEventResponse extends BaseEventResponse {
    public BigInteger alg;
  }
}
