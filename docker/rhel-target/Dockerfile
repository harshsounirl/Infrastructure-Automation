# RHEL-equivalent target container for local Ansible testing
# Uses AlmaLinux 9 (binary-compatible RHEL 9 rebuild)
FROM almalinux:9

# Install SSH server + sudo (required for Ansible connection)
RUN dnf install -y \
      openssh-server \
      openssh-clients \
      sudo \
      python3 \
      python3-pip \
      systemd \
      firewalld \
    && dnf clean all

# Configure SSH
RUN ssh-keygen -A \
    && sed -i \
         -e 's/#PermitRootLogin.*/PermitRootLogin no/' \
         -e 's/#PasswordAuthentication.*/PasswordAuthentication yes/' \
         -e 's/#PubkeyAuthentication.*/PubkeyAuthentication yes/' \
         /etc/ssh/sshd_config \
    && mkdir -p /var/run/sshd

# Create Ansible service account
ARG ANSIBLE_USER=ansible
ARG ANSIBLE_PASSWORD=ansible123
RUN useradd -m -s /bin/bash ${ANSIBLE_USER} \
    && echo "${ANSIBLE_USER}:${ANSIBLE_PASSWORD}" | chpasswd \
    && echo "${ANSIBLE_USER} ALL=(ALL) NOPASSWD: ALL" \
       > /etc/sudoers.d/${ANSIBLE_USER} \
    && chmod 440 /etc/sudoers.d/${ANSIBLE_USER}

# Copy dev public key for passwordless auth (optional)
RUN mkdir -p /home/${ANSIBLE_USER}/.ssh \
    && chmod 700 /home/${ANSIBLE_USER}/.ssh \
    && chown -R ${ANSIBLE_USER}:${ANSIBLE_USER} /home/${ANSIBLE_USER}/.ssh

EXPOSE 22

# Start sshd in the foreground
CMD ["/usr/sbin/sshd", "-D"]
