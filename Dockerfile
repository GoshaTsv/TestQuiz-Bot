
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

# Line 6: The command to run your app
# This is what Railway executes to start your program
CMD ["java", "-cp", "src/main/java", "Main"]