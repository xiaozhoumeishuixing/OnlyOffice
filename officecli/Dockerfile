FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL -o /usr/local/bin/officecli \
       "https://github.com/iOfficeAI/OfficeCLI/releases/latest/download/officecli-linux-x64" \
    && chmod +x /usr/local/bin/officecli \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["officecli"]
CMD ["--help"]
