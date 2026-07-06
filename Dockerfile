# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# Uses the official Maven + JDK 21 image to compile and package the JAR.
# Internet access is required at this stage to pull Maven dependencies.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom.xml first and resolve dependencies before copying source code.
# This exploits Docker's layer cache: if only source files change (not pom.xml),
# this layer is reused and dependencies are NOT re-downloaded.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy all source and compile/package
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# Minimal JRE-only image — no JDK, no Maven, no build tooling.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy only the built JAR from the build stage
COPY --from=build /build/target/moneymap.jar app.jar

# Create the data directory. In production this is always a mounted volume
# (see docker-compose.yml), but the directory must exist for the container
# to start cleanly even if a volume is not mounted.
RUN mkdir -p /app/data

VOLUME /app/data

# Environment variables with defaults.
ENV PORT=1010
ENV DATA_DIR=/app/data
ENV JVM_OPTS="-Xms128m -Xmx384m"
ENV TZ=Asia/Kolkata

EXPOSE 1010

# Use shell form to allow $JVM_OPTS expansion
ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar app.jar"]
