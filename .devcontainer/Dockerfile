FROM python:3.12-slim

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    openssh-client \
    sshpass \
    git \
    curl \
    jq \
    make \
    docker.io \
    && rm -rf /var/lib/apt/lists/*

# Install Ansible and linting tools
RUN pip install --no-cache-dir \
    ansible==9.* \
    ansible-lint==6.* \
    molecule==6.* \
    molecule-docker==2.* \
    paramiko \
    jmespath

# Install ansible collections
RUN ansible-galaxy collection install \
    community.general \
    ansible.posix

# Create non-root user for dev
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && usermod -aG docker $USERNAME

# Set working directory
WORKDIR /workspace

USER $USERNAME
