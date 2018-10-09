FROM node:8-alpine

RUN apk update && apk upgrade && \
    apk add --no-cache git sed python make g++

RUN git clone https://github.com/mix-blockchain/block-explorer.git explorer

WORKDIR explorer/

RUN npm install

COPY *.patch ./
COPY favicon.png public/favicon.png

RUN git apply *.patch

CMD npm start

# List Exposed Ports
EXPOSE 3000
