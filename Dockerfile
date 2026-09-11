FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /builder

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests

RUN java -Djarmode=tools -jar target/mkpay-0.0.1-SNAPSHOT.jar extract --destination target/extracted


FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S mkpaygroup && adduser -S mkpayuser -G mkpaygroup
USER mkpayuser:mkpaygroup

COPY --from=builder /builder/target/extracted/dependencies/ ./
COPY --from=builder /builder/target/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/target/extracted/application/ ./

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]