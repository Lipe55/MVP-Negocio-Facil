# syntax=docker/dockerfile:1
FROM golang:1.27.1 AS build
WORKDIR /src
ADD https://github.com/ReceitaFederal/calculadora-rtc-gerador-danfse.git#8d79e6b2f7fad6375340afcb51c85ed370c64a14 /src
# Upstream ships generated Swagger assets at this revision.
RUN go mod download
RUN CGO_ENABLED=0 go build -trimpath -o /danfse-service ./cmd/danfse

FROM alpine:3.23
ENV TZ=America/Sao_Paulo
ENV ZONEINFO=/usr/share/zoneinfo.zip
COPY --from=build /usr/local/go/lib/time/zoneinfo.zip /usr/share/zoneinfo.zip
COPY --from=build /danfse-service /usr/local/bin/danfse-service
COPY LICENSE /usr/share/licenses/danfse-service/LICENSE
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/danfse-service"]
