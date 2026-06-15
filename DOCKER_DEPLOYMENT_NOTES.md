# Docker Deployment Notes

## Local Build

From the project root:

```sh
docker buildx build --platform linux/amd64 -t vops-updated-local .
```

Use `--platform linux/amd64` for ECS/Fargate images, especially when building from a Mac ARM machine. This avoids the common ECS runtime issue:

```text
exec ... java: exec format error
```

## Local Run

Pass Zenodo credentials through environment variables only. Do not hard-code tokens in Dockerfiles, scripts, source, or documentation.

```sh
docker run --rm -p 8080:8080 -e ZENODO_ACCESS_TOKEN="$ZENODO_ACCESS_TOKEN" -e SPRING_PROFILES_ACTIVE=sandbox vops-updated-local
```

Then open:

```text
http://localhost:8080
```

## ECR Build And Push Template

Login to ECR:

```sh
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin 383418747870.dkr.ecr.us-east-1.amazonaws.com
```

Build and push the ECS/Fargate-compatible image:

```sh
docker buildx build --platform linux/amd64 \
  -t 383418747870.dkr.ecr.us-east-1.amazonaws.com/vops-updated-service:manual-v1 \
  --push .
```

Image tag:

```text
383418747870.dkr.ecr.us-east-1.amazonaws.com/vops-updated-service:manual-v1
```

## Verification Checklist

- Container starts successfully on port `8080`.
- `http://localhost:8080` loads in a browser.
- `git --version` works inside the container:

```sh
docker run --rm --entrypoint git vops-updated-local --version
```

- `syft version` works inside the container:

```sh
docker run --rm --entrypoint syft vops-updated-local version
```

- The app can access `/api/sbom`. For a route-level check:

```sh
curl -i -X POST http://localhost:8080/api/sbom \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Use a public HTTPS Git repository URL only when running a full SBOM workflow.

## Security Notes

- Never commit real Zenodo tokens.
- Pass `ZENODO_ACCESS_TOKEN` through an environment variable for local runs.
- Use AWS Secrets Manager for ECS/Fargate deployments.
- Rotate any exposed Zenodo tokens immediately.
- Use the `sandbox` profile first.
- Do not upload private or proprietary repositories.

## Runtime Defaults

The Docker image sets:

```text
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=sandbox
SBOM_SYFT_PATH=/usr/local/bin/syft
```

The application runs the generated Spring Boot jar at:

```text
/app/app.jar
```
