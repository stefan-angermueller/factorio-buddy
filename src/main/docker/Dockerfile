FROM amazoncorretto:17-alpine

ADD target/FactorioBuddy-*.jar /application/application.jar

VOLUME /application/data
VOLUME /tmp

WORKDIR /application

EXPOSE 8080

ENV rcon.host=
ENV rcon.port=27015
ENV rcon.password=

ENTRYPOINT exec java -jar application.jar