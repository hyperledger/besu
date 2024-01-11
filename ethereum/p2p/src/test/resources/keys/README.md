Following instructions were used to set up various certificates and keystores.

The certificates hierarchy 
~~~
                 root.ca.besu.com
                        | 
                 inter.ca.besu.com
                        |      
partner1.ca.besu.com             partner2.ca.besu.com
       |                               |
client1.partner1.besu.com        client1.partner2.besu.com
client2.partner1.besu.com        client2.partner2.besu.com 
~~~

Partner1 clients
Client1 - EC key, backed by PKCS11/nssdb as well.
Client2 - in certificate revoked list (CRL)

Partner2 clients
Client1 
Client2 - in certificate revoked list (CRL)

InvalidPartner1 clients
Client1 - not trusted by Partner1 and Partner2 due to different CA hierarchy 



## Lib NSS Setup Instructions:
Linux: `sudo apt install libnss3-tools`
Mac OS: 
1. `brew install nss`
2. Create symlink for libnss3 and lonsoftokn3 in your JDK's lib (or update LD_LIBRARY_PATH globally)
~~~
ln -s /opt/homebrew/lib/libnss3.dylib <jdk_path>/lib/libnss3.dylib
ln -s /opt/homebrew/lib/libsoftokn3.dylib <jdk_path>/lib/libsoftokn3.dylib
~~~

## Root CA, InterCA, partner1 CA, partner2 CA
All the CA keystores will be generated in `ca_certs` directory. 

Generate Root CA (validity 100 years)
~~~
cd ca_certs

export ROOT_CA_KS=root_ca.p12
export INTER_CA_KS=inter_ca.p12
export PARTNER1_CA_KS=partner1_ca.p12
export PARTNER2_CA_KS=partner2_ca.p12
~~~
~~~
keytool -genkeypair -alias root_ca -dname "CN=root.ca.besu.com" -ext bc:c -keyalg RSA \
-sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $ROOT_CA_KS

keytool -genkeypair -alias inter_ca -dname "CN=inter.ca.besu.com" \
-ext bc:c=ca:true,pathlen:1 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $INTER_CA_KS

keytool -genkeypair -alias partner1_ca -dname "CN=partner1.ca.besu.com" \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $PARTNER1_CA_KS

keytool -genkeypair -alias partner2_ca -dname "CN=partner2.ca.besu.com" \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $PARTNER2_CA_KS
~~~

CSR, Signing and re-import
~~~
keytool -storepass test123 -keystore $ROOT_CA_KS -alias root_ca -exportcert -rfc > root_ca.pem

keytool -storepass test123 -keystore $INTER_CA_KS -certreq -alias inter_ca \
| keytool -storepass test123 -keystore $ROOT_CA_KS -gencert -validity 36500 -alias root_ca \
-ext bc:c=ca:true,pathlen:1 -ext ku:c=dS,kCS,cRLs -rfc > inter_ca.pem

cat root_ca.pem >> inter_ca.pem

keytool -keystore $INTER_CA_KS -importcert -alias inter_ca \
-storepass test123 -noprompt -file ./inter_ca.pem

keytool -storepass test123 -keystore $PARTNER1_CA_KS -certreq -alias partner1_ca \
| keytool -storepass test123 -keystore $INTER_CA_KS -gencert -validity 36500 -alias inter_ca \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs -rfc > partner1_ca.pem

keytool -storepass test123 -keystore $PARTNER2_CA_KS -certreq -alias partner2_ca \
| keytool -storepass test123 -keystore $INTER_CA_KS -gencert -validity 36500 -alias inter_ca \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs -rfc > partner2_ca.pem

cat root_ca.pem >> partner1_ca.pem
cat root_ca.pem >> partner2_ca.pem

keytool -keystore $PARTNER1_CA_KS -importcert -alias partner1_ca \
-storepass test123 -noprompt -file ./partner1_ca.pem

keytool -keystore $PARTNER2_CA_KS -importcert -alias partner2_ca \
-storepass test123 -noprompt -file ./partner2_ca.pem

cd ..
~~~

---

## Client certificates
Following folder names are used
- 
- `partner1client1`
- `partner1client2rvk`
- `partner2client1`
- `partner2client2rvk`

Modify the partner and client variables while running following commands accordingly.

## Truststore
Create truststore for each partner and copy in appropriate client directories
~~~
cd partner2client1

export OU=partner2

keytool -import -trustcacerts -alias root_ca \
-file ../ca_certs/root_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

keytool -import -trustcacerts -alias inter_ca \
-file ../ca_certs/inter_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

keytool -import -trustcacerts -alias ${OU}_ca \
-file ../ca_certs/${OU}_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

~~~

Cd to appropriate client directory and generate the certificates.
Note: The keyalg for partner1client1 (EC) is different than others

Modify the export command.
~~~
cd partner1client1

export OU=partner1
export CLIENT=client1
~~~

EC Key generation (for partner1 client 1)
~~~
keytool -genkeypair -keystore $CLIENT.p12 -storepass test123 -alias $CLIENT \
-keyalg EC -groupname secp256r1 -validity 36500 \
-dname "CN=$CLIENT.$OU.besu.com, OU=$OU, O=Besu, L=Brisbane, ST=QLD, C=AU" \
-ext san=dns:localhost,ip:127.0.0.1
~~~
RSA key generation
~~~
keytool -genkeypair -keystore $CLIENT.p12 -storepass test123 -alias $CLIENT \
-keyalg RSA -validity 36500 \
-dname "CN=$CLIENT.$OU.besu.com, OU=$OU, O=Besu, L=Brisbane, ST=QLD, C=AU" \
-ext san=dns:localhost,ip:127.0.0.1
~~~

CSR and reimport
~~~
keytool -storepass test123 -keystore "$CLIENT.p12" -certreq -alias $CLIENT \
| keytool -storepass test123 -keystore "../ca_certs/${OU}_ca.p12" -gencert -validity 36500 -alias ${OU}_ca \
-ext ku:c=digitalSignature,nonRepudiation,keyEncipherment -ext eku=sA,cA -rfc > "$CLIENT.pem"

cat ../ca_certs/root_ca.pem >> $CLIENT.pem

keytool -keystore $CLIENT.p12 -importcert -alias $CLIENT \
-storepass test123 -noprompt -file ./$CLIENT.pem
~~~

Verify
~~~
keytool -keystore $CLIENT.p12 -storepass test123 -list -v
~~~


## NSS database setup
The nss database will be setup in `partner1client1` directory.

Initialize empty nss database. Modify client and OU export.
~~~
export CLIENT=client1
export OU=partner1

mkdir nssdb
echo "test123" > nsspin.txt
certutil -N -d sql:nssdb -f nsspin.txt
touch ./nssdb/secmod.db
~~~

Import client1 keystore into nss
~~~
pk12util -i ./$CLIENT.p12 -d sql:nssdb -k nsspin.txt -W test123
certutil -M -n "CN=root.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt
certutil -M -n "CN=inter.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt
certutil -M -n "CN=${OU}.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt

certutil -d sql:nssdb -f nsspin.txt -L
~~~

PKCS11 Configuration file
~~~
cat <<EOF >./nss.cfg
name = NSScrypto-${OU}-${CLIENT}
nssSecmodDirectory = ./src/test/resources/keys/partner1client1/nssdb
nssDbMode = readOnly
nssModule = keystore
showInfo = true
EOF
~~~

## CRL certificate
As mentioned at the start of README, `partner1client2` and `partner2client2` certificates are in revoke list.
The crl list is maintained under `crl` folder.

Partner1 and Partner2 private keys are required to exported in pem format.

Following operations are performed under `crl` folder. 
_Note: On Mac OS, using `gnutls-certtool`. On Linux, `certtool` can be used._

~~~
openssl pkcs12 -nodes -in ../ca_certs/partner1_ca.p12 -out partner1_ca_key.pem -passin pass:test123 -nocerts
openssl pkcs12 -nodes -in ../ca_certs/partner1_ca.p12 -out partner1_ca.pem -passin pass:test123 -nokeys

openssl pkcs12 -nodes -in ../ca_certs/partner2_ca.p12 -out partner2_ca_key.pem -passin pass:test123 -nocerts
openssl pkcs12 -nodes -in ../ca_certs/partner2_ca.p12 -out partner2_ca.pem -passin pass:test123 -nokeys

openssl pkcs12 -nokeys -clcerts -in ../partner1client2rvk/client2.p12 -out partner1client2.pem -passin pass:test123
openssl pkcs12 -nokeys -clcerts -in ../partner2client2rvk/client2.p12 -out partner2client2.pem -passin pass:test123

gnutls-certtool --generate-crl --load-ca-privkey ./partner1_ca_key.pem --load-ca-certificate ./partner1_ca.pem \
--load-certificate ./partner1client2.pem >> crl.pem
gnutls-certtool --generate-crl --load-ca-privkey ./partner2_ca_key.pem --load-ca-certificate ./partner2_ca.pem \
--load-certificate ./partner2client2.pem >> crl.pem

keytool -printcrl -file ./crl.pem

rm *_ca_key.pem
rm *_ca.pem
rm *client2.pem
~~~
