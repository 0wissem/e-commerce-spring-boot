# Project Setup Guide

## Prerequisites

- Java 17+
- Maven (or use the included `./mvnw` wrapper)
- Docker
- AWS CLI (for deployment)
- k6 (for load testing)

---

## Run Locally (Dev Mode)

The dev profile uses an H2 in-memory database — no setup required.

```bash
./mvnw spring-boot:run
```

App runs on: `http://localhost:8080`

---

## Run with Docker Locally

```bash
# Build image
docker build -t spring-boot-0 .

# Run with dev profile
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev spring-boot-0
```

---

## Run with PostgreSQL (Prod Mode)

You need a running PostgreSQL instance. Set the following environment variables:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://<HOST>:<PORT>/<DB_NAME>
export SPRING_DATASOURCE_USERNAME=<USERNAME>
export SPRING_DATASOURCE_PASSWORD=<PASSWORD>

./mvnw spring-boot:run
```

---

## Deploy to AWS (Full Setup)

### 1. AWS Services Required
- **RDS** — PostgreSQL database
- **ECR** — Docker image registry
- **Elastic Beanstalk** — Docker environment
- **IAM** — roles and permissions

### 2. IAM Setup
Create a custom EC2 instance profile role (`eb-ec2-ecr-pull-role`) with these policies:
- `AWSElasticBeanstalkWebTier`
- `AWSElasticBeanstalkWorkerTier`
- `AWSElasticBeanstalkMulticontainerDocker`
- `AmazonEC2ContainerRegistryReadOnly`

Assign this role as the EC2 instance profile in your EB environment.

### 3. ECR Setup
```bash
# Create ECR repository named: spring-boot-0
aws ecr create-repository --repository-name spring-boot-0 --region eu-north-1
```

### 4. Elastic Beanstalk Setup
- Platform: **Docker**
- Environment name: match `environment_name` in `.github/workflows/deploy.yml`
- Set these environment variables in EB:
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SERVER_PORT=5000`

### 5. GitHub Secrets
Add these secrets to your GitHub repository (Settings → Secrets):

| Secret | Description |
|---|---|
| `AWS_ACCESS_KEY_ID` | IAM user access key |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key |

The IAM user needs these permissions:
- `AmazonEC2ContainerRegistryFullAccess`
- `AWSElasticBeanstalkFullAccess`
- `AmazonS3FullAccess`

### 6. Deploy
Push to `dev` or `master` — GitHub Actions handles the rest:
1. Builds Docker image
2. Pushes to ECR
3. Deploys `Dockerrun.aws.json` to Elastic Beanstalk

---

## Load Testing with k6

### Install k6
```bash
brew install k6        # macOS
sudo dnf install k6    # Amazon Linux / EC2
```

### Run stress tests
```bash
# Terminal 1 — stress search endpoint
k6 run k6/search-stress.js

# Terminal 2 — measure orders degradation simultaneously
k6 run k6/orders-measure.js
```

### Stream results to Grafana Cloud
```bash
k6 login cloud --token <YOUR_GRAFANA_TOKEN>
k6 run --out=cloud k6/search-stress.js
```

---

## Database Migrations

Flyway runs automatically on startup. Migration files are in:
```
src/main/resources/db/migration/
```

To add a new migration, create a file following the naming convention:
```
V{next_number}__{description}.sql
```

> **Important:** Never modify an already-applied migration file. Always create a new version.