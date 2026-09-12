FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /builder

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests

RUN java -Djarmode=tools -jar target/mkpay-*.jar extract --layers --launcher --destination target/extracted


FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S mkpaygroup && adduser -S mkpayuser -G mkpaygroup

COPY --chown=mkpayuser:mkpaygroup --from=builder /builder/target/extracted/dependencies/ ./
COPY --chown=mkpayuser:mkpaygroup --from=builder /builder/target/extracted/spring-boot-loader/ ./
COPY --chown=mkpayuser:mkpaygroup --from=builder /builder/target/extracted/snapshot-dependencies/ ./
COPY --chown=mkpayuser:mkpaygroup --from=builder /builder/target/extracted/application/ ./

USER mkpayuser:mkpaygroup

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]