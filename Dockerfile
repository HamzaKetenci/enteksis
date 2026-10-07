# ==========================================
# Aşama 1: Derleme (Build Stage)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Bağımlılıkları önbelleğe almak için önce sadece pom.xml'i kopyala
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kaynak kodları kopyala ve testleri atlayarak paketle (testler CI/yerel ortamda koşar)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Aşama 2: Çalıştırma (Runtime Stage)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Güvenlik: Non-root kullanıcı oluştur
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Builder aşamasından oluşan JAR dosyasını kopyala
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Sahipliği non-root kullanıcıya devret
RUN chown -R appuser:appgroup /app

# Non-root kullanıcıya geç
USER appuser

# Render ve standart container portu
EXPOSE 8080

# JVM bellek optimizasyonu (Container RAM'inin en fazla %75'i)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
