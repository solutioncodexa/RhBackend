# RHFlowBack

Backend Spring Boot 3.4 / Java 21 for RHFlow Maroc. No servlet context-path: the frontend calls `http://localhost:8080/auth/login`.

## Run

Requires JDK 21 and Maven on your PATH (`JAVA_HOME` pointing at a JDK 21).

```bash
cd RHFlowBack
mvn spring-boot:run
```

Example if Maven/Java are not on PATH:

```bash
set JAVA_HOME=%USERPROFILE%\.jdks\ms-21.0.9
set PATH=%JAVA_HOME%\bin;%USERPROFILE%\apache-maven-3.9.9\bin;%PATH%
mvn spring-boot:run
```

API: http://localhost:8080  
H2 console: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:file:./data/rhflow` — user `sa`, empty password.

Frontend (`RHFlowFront`) runs on port 4300. CORS allows 4300 and 5173.

Optional PostgreSQL:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Demo accounts (seeded if the database is empty)

| Email | Password | Role |
| --- | --- | --- |
| superadmin@rhflow.ma | Super@123456 | SUPER_ADMIN |
| admin@rhflow.ma | Admin@123456 | ADMIN |
| manager@rhflow.ma | Manager@123456 | MANAGER |
| employee@rhflow.ma | Employee@123456 | EMPLOYEE |

Tenant demo: Technova Maroc (`technova`), plan PRO.

JWT secret in `application.properties` is for local demo only. Do not commit production secrets.
