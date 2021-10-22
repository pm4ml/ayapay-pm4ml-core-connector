FROM openjdk:8-jdk-alpine

ARG JAR_FILE=core-connector/target/*.jar

COPY ${JAR_FILE} app.jar

ENV MLCONN_OUTBOUND_ENDPOINT=http://simulator:3004
ENV DFSP_NAME="DFSP CO. LTD."
ENV DFSP_HOST="https://localhost/api"
ENV DFSP_USERNAME="username"
ENV DFSP_PASSWORD="password"
ENV DFSP_API_VERSION="v1"
ENV DFSP_LOGIN_ORGANIZATIONID="123"
ENV DFSP_LOGIN_ORGANIZATIONNAME="dfsp name"
ENV DFSP_LOGIN_PASSWORD="password"

ENTRYPOINT ["java", "-Dml-conn.outbound.host=${MLCONN_OUTBOUND_ENDPOINT}", "-Ddfsp.name=${DFSP_NAME}", "-Ddfsp.host=${DFSP_HOST}", "-Ddfsp.username=${DFSP_USERNAME}", "-Ddfsp.password=${DFSP_PASSWORD}", -Ddfsp.api.version=${DFSP_API_VERSION}, -Ddfsp.login.organizationId=${DFSP_LOGIN_ORGANIZATIONID}, -Ddfsp.login.organizationName=${ENV DFSP_LOGIN_ORGANIZATIONNAME}, -Ddfsp.login.password=${ENV DFSP_LOGIN_PASSWORD}","-jar", "/app.jar"]

EXPOSE 3003