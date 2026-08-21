FROM golang:1.26-alpine AS builder
WORKDIR /app
COPY go.mod main.go ./
RUN go mod tidy
RUN go build -o /app/benchmark-gin main.go

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/benchmark-gin .
EXPOSE 8080
CMD ["./benchmark-gin"]
