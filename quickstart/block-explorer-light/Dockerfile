FROM nginx:alpine
COPY dist /usr/share/nginx/html
RUN sed -i 's@NODE_ENV:"production",BASE_URL:"/"@NODE_URL:"/jsonrpc",CONNECTION_TYPE:"json_rpc"@g' /usr/share/nginx/html/js/*.js
COPY nginx.vh.conf /etc/nginx/conf.d/default.conf
