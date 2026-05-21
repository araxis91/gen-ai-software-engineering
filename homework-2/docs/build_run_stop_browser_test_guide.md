# Build, Run, Stop, and Browser Testing Guide
## 1. Prerequisites
- Java 25
- Maven 3.9+

Quick checks:
```bash
java -version
mvn -v
```

## 2. Build the application
Run:
```bash
mvn clean package
```

This compiles the project, runs tests, and builds the runnable JAR:
`target/intelligent-customer-support-0.0.1-SNAPSHOT.jar`

## 3. Run the application
Option A (recommended for development):
```bash
mvn spring-boot:run
```

Option B (run the packaged JAR):
```bash
java -jar target/intelligent-customer-support-0.0.1-SNAPSHOT.jar
```

When startup is successful, the API is available at:
`http://localhost:8080`

## 4. Stop the application
If running in the same terminal session, press:
- `Ctrl + C`

If it is running in the background:
```bash
lsof -ti tcp:8080 -sTCP:LISTEN
kill <PID>
```

If the process does not stop gracefully:
```bash
kill -9 <PID>
```

## 5. Test the application in a browser
### Step 1: Verify API is up
Open:
- `http://localhost:8080/tickets`

You should see a JSON response with paging fields and a `content` array.

### Step 2: Test pagination and filters
Open:
- `http://localhost:8080/tickets?page=0&size=5`
- `http://localhost:8080/tickets?priority=high&page=0&size=10`

### Step 3: Open a ticket by ID
1. Open `http://localhost:8080/tickets`
2. Copy one `id` from the `content` array
3. Open `http://localhost:8080/tickets/{id}` with that value

### Step 4: If `content` is empty, import sample data
Use:
```bash
curl -X POST "http://localhost:8080/tickets/import" \
  -F "file=@sample_tickets.csv;type=text/csv"
```

Then refresh `http://localhost:8080/tickets` in the browser.
