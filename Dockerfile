FROM eclipse-temurin:17-jdk-jammy AS base
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:resolve
COPY src ./src

FROM base AS build
RUN ./mvnw -B -Dmaven.test.skip=true package

# Used by docker-compose.dev.yml
FROM base AS development
CMD ["./mvnw", "spring-boot:run", "-Dspring-boot.run.profiles=mysql", "-Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000'"]

FROM eclipse-temurin:17-jre-jammy AS production
RUN groupadd --gid 10001 petclinic \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin petclinic
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/spring-petclinic-*.jar /app/spring-petclinic.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/spring-petclinic.jar"]
