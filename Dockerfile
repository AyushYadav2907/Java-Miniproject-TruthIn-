# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies first (cached unless pom.xml changes)
COPY backend/pom.xml backend/pom.xml
RUN cd backend && mvn -q -B dependency:go-offline

# Build the application (backend code + frontend + database scripts)
COPY backend ./backend
COPY frontend ./frontend
COPY database ./database
RUN cd backend && mvn -q -B -DskipTests package

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/backend/target/truthscan.jar app.jar

EXPOSE 8080
# Keep memory usage friendly for small/free hosting plans
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
