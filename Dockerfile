FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package
CMD ["java", "-jar", "target/MakerTime_Ryzen10950x4DTelegramBot-1.0-SNAPSHOT.jar"]