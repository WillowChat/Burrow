FROM openjdk:9.0.1-jre
ARG jar
ARG config
WORKDIR /
ADD $jar Burrow.jar
ADD $config burrow.yaml
EXPOSE 6667
CMD java -jar Burrow.jar
