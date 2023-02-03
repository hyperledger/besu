See `ethereum/p2p/src/test/resources/keys/README.md` which describes the details on how the 
certificates are created. The same CA are used to create miner1-miner6. 

For `PkiQbftAcceptanceTest`: 
`miner1`-`miner5` are signed with `partner1_ca` and `miner6` is signed with `partner2_ca`.
`miner5` and `miner6` are revoked and added in the crl list.

Sample shell script that can be executed to recreate the certificates
~~~
#! /bin/sh

names=("partner1:miner1" "partner1:miner2" "partner1:miner3" "partner1:miner4" "partner1:miner5" "partner2:miner6")
crls=("partner1:miner5" "partner2:miner6")
KEY_ALG="EC -groupname secp256r1"
#KEY_ALG="RSA -keysize 2048"

##########
CA_CERTS_PATH=./ca_certs
ROOT_CA_KS=$CA_CERTS_PATH/root_ca.p12
INTER_CA_KS=$CA_CERTS_PATH/inter_ca.p12
CRL_DIR=./crl

keytool -exportcert -keystore $ROOT_CA_KS -storepass test123 -alias root_ca -rfc -file $CA_CERTS_PATH/root_ca.pem

echo "Generating miner keystores..."
### Generate client keystores
for name in "${names[@]}"
do
  IFS=':' read -r -a array <<< "$name"
  partner=${array[0]}
  client=${array[1]}

  PARTNER_CA_KEYSTORE="$CA_CERTS_PATH/${partner}_ca.p12"
  CLIENT_PATH="./${client}"
  KEYSTORE_PATH="./$CLIENT_PATH/${client}.p12"
  NSSDB_PATH="${CLIENT_PATH}/nssdb"

  echo "$PARTNER_CA_KEYSTORE"

  mkdir -p $NSSDB_PATH

  echo "Generating keystore for Partner $partner Client $client"
  keytool -genkeypair -keystore $KEYSTORE_PATH -storepass test123 -alias ${client} \
  -keyalg $KEY_ALG -validity 36500 \
  -dname "CN=localhost, OU=${partner}" \
  -ext san=dns:localhost,ip:127.0.0.1

  echo "Creating CSR for $client and signing it with ${partner}_ca"
  keytool -storepass test123 -keystore $KEYSTORE_PATH -certreq -alias ${client} \
  | keytool -storepass test123 -keystore $PARTNER_CA_KEYSTORE -gencert -alias "${partner}_ca" -ext ku:c=digitalSignature,nonRepudiation,keyEncipherment -ext eku=sA,cA \
  -rfc > "${CLIENT_PATH}/${client}.pem"

  echo "Concat root_ca.pem to ${client}.pem"
  cat "${CA_CERTS_PATH}/root_ca.pem" >> "${CLIENT_PATH}/${client}.pem"

  echo "Importing signed $client.pem CSR into $KEYSTORE_PATH"
  keytool -keystore $KEYSTORE_PATH -importcert -alias $client \
  -storepass test123 -noprompt -file "${CLIENT_PATH}/${client}.pem"

  echo "Converting p12 to jks"
  keytool -importkeystore -srckeystore $KEYSTORE_PATH -srcstoretype PKCS12 -destkeystore "$CLIENT_PATH/${client}.jks"  -deststoretype JKS -srcstorepass test123 -deststorepass test123 -srcalias $client -destalias $client -srckeypass test123 -destkeypass test123 -noprompt

  echo "Initialize nss"
  echo "test123" > ${CLIENT_PATH}/nsspin.txt
  certutil -N -d sql:${NSSDB_PATH} -f "${CLIENT_PATH}/nsspin.txt"
  # hack to make Java SunPKCS11 work with new sql version of nssdb
  touch ${NSSDB_PATH}/secmod.db

  pk12util -i $KEYSTORE_PATH -d sql:${NSSDB_PATH} -k ${CLIENT_PATH}/nsspin.txt -W test123
  echo "Fixing truststores in sql:${NSSDB_PATH}"
  certutil -M -n "CN=root.ca.besu.com"       -t CT,C,C -d sql:"$NSSDB_PATH" -f ${CLIENT_PATH}/nsspin.txt
  certutil -M -n "CN=inter.ca.besu.com"      -t CT,C,C -d sql:"$NSSDB_PATH" -f ${CLIENT_PATH}/nsspin.txt
  certutil -M -n "CN=${partner}.ca.besu.com" -t CT,C,C -d sql:"$NSSDB_PATH" -f ${CLIENT_PATH}/nsspin.txt

  certutil -d sql:"$NSSDB_PATH" -f nsspin.txt -L

  echo "Creating pkcs11 nss config file"
  cat <<EOF >${CLIENT_PATH}/nss.cfg
name = NSScrypto-${partner}-${client}
nssSecmodDirectory = ./src/test/resources/pki-certs/${client}/nssdb
nssDbMode = readOnly
nssModule = keystore
showInfo = true
EOF

  # remove pem files
  rm "${CLIENT_PATH}/${client}.pem"

  # create truststore
  echo "Creating truststore ..."
  keytool -exportcert -keystore $ROOT_CA_KS  -storepass test123 -alias root_ca -rfc   | keytool -import -trustcacerts -alias root_ca -keystore "${CLIENT_PATH}/truststore.p12" -storepass test123 -noprompt
  keytool -exportcert -keystore $INTER_CA_KS -storepass test123 -alias inter_ca -rfc | keytool -import -trustcacerts -alias inter_ca -keystore "${CLIENT_PATH}/truststore.p12" -storepass test123 -noprompt
  keytool -exportcert -keystore $PARTNER_CA_KEYSTORE  -storepass test123 -alias "${partner}_ca" -rfc   | keytool -import -trustcacerts -alias "${partner}_ca" -keystore "${CLIENT_PATH}/truststore.p12" -storepass test123 -noprompt

done
rm $CA_CERTS_PATH/root_ca.pem
echo "Keystores and nss database created"

## create crl list
mkdir -p $CRL_DIR
rm $CRL_DIR/crl.pem

for crl in "${crls[@]}"
do
  IFS=':' read -r -a array <<< "$crl"
  partner=${array[0]}
  client=${array[1]}

  echo "Exporting CA certificate and private key"
  openssl pkcs12 -nodes -in "$CA_CERTS_PATH/${partner}_ca.p12" -out "$CRL_DIR/${partner}_ca_key.pem" -passin pass:test123 -nocerts
  openssl pkcs12 -nodes -in "$CA_CERTS_PATH/${partner}_ca.p12" -out "$CRL_DIR/${partner}_ca.pem" -passin pass:test123 -nokeys

  echo "Export $client certificate"
  openssl pkcs12 -nodes -in "./${client}/${client}.p12" -out "$CRL_DIR/${client}.pem" -passin pass:test123 -nokeys

  ## On Mac, use gnutls-certtool, on Linux use certtool
  echo "Creating crl"
  printf '365\n\n' | gnutls-certtool --generate-crl --load-ca-privkey "$CRL_DIR/${partner}_ca_key.pem" --load-ca-certificate "$CRL_DIR/${partner}_ca.pem" \
  --load-certificate "$CRL_DIR/${client}.pem" >> $CRL_DIR/crl.pem

  rm "$CRL_DIR/${partner}_ca_key.pem"
  rm "$CRL_DIR/${partner}_ca.pem"
  rm "$CRL_DIR/${client}.pem"

done

~~~
