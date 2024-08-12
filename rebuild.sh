./gradlew build -x test
./gradlew installDist -x test
sudo systemctl restart besu.service
