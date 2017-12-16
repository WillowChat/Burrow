FROM openjdk:8u151-jre
ARG jar
WORKDIR /
ADD $jar Burrow.jar
EXPOSE 6667
CMD java -jar Burrow.jar