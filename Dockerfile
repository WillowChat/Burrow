FROM openjdk:9.0.1-jre
ARG jar
WORKDIR /
ADD $jar Burrow.jar
EXPOSE 6667
CMD java -jar Burrow.jar 0.0.0.0 6667
