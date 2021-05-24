FROM openjdk:8-jdk

COPY ./target/*.jar /app/romeway.jar

WORKDIR /app

RUN mkdir -p ${CONFIG_PATH:-configuration}

CMD ["java", "-jar", "romeway.jar"]