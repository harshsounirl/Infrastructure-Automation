# Zabbix Monitoring Setup Guide

This document covers deploying Zabbix on Azure, monitoring Proxmox via REST API,
configuring MS Teams alerting, and setting up a public status page.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Deploy Zabbix on Azure (Marketplace Image)](#deploy-zabbix-on-azure-marketplace-image)
3. [Import Proxmox Monitoring Template](#import-proxmox-monitoring-template)
4. [MS Teams Alerting](#ms-teams-alerting)
5. [Status Page with Uptime Kuma](#status-page-with-uptime-kuma)
6. [Alternative Status Page Options](#alternative-status-page-options)

---

## Architecture Overview

```
Proxmox (on-prem)
      │
      │ REST API (port 8006)
      ▼
Zabbix Server (Azure VM)
      │
      ├──► MS Teams (Webhook Alerts)
      │
      └──► Uptime Kuma (Public Status Page)
```

**Network requirement:** Azure VPN Gateway or Site-to-Site VPN between Azure and
Proxmox on-prem environment for secure API communication.

---

## Deploy Zabbix on Azure (Marketplace Image)

### Prerequisites
- Azure subscription
- Azure CLI installed or access to Azure Portal

### Step 1: Deploy from Marketplace

1. Go to **portal.azure.com**
2. Search **"Zabbix"** in the top search bar → select **Marketplace**
3. Choose the image published by **"Zabbix LLC"** → select **version 7.0 LTS**
4. Click **Create**

### Step 2: Configure VM Settings

| Field | Recommended Value |
|-------|------------------|
| Resource Group | `zabbix-rg` |
| VM Name | `zabbix-server` |
| Region | Closest to Proxmox environment |
| Size | `Standard_B2s` (2 vCPU, 4GB RAM) minimum |
| OS Disk | Standard SSD, 64GB |
| Authentication | SSH public key |
| Username | `azureuser` |

### Step 3: Configure Networking

| Field | Value |
|-------|-------|
| Virtual Network | Create new or use existing |
| Public IP | Standard SKU |
| Inbound Ports | 80 (HTTP), 443 (HTTPS), 22 (SSH) |

Open additional ports via Azure CLI after deployment:

```bash
# Zabbix agent port
az vm open-port --resource-group zabbix-rg --name zabbix-server --port 10051 --priority 102
```

### Step 4: Get VM Public IP

```bash
az vm show -d -g zabbix-rg -n zabbix-server --query publicIps -o tsv
```

### Step 5: Access the Web UI

Navigate to: `http://<public-ip>/zabbix`

| Field | Default Value |
|-------|--------------|
| Username | `Admin` |
| Password | `zabbix` |

> **Important:** Change the default password immediately after first login.

### Step 6: Secure SSH Access

Restrict SSH to your IP only:

```bash
az network nsg rule update \
  --resource-group zabbix-rg \
  --nsg-name <your-nsg-name> \
  --name SSH \
  --source-address-prefixes <your-public-ip>
```

### Estimated Monthly Cost

| Resource | Approx. Cost |
|----------|-------------|
| `Standard_B2s` VM | ~$30–40/month |
| OS Disk (64GB SSD) | ~$5–10/month |
| Public IP + bandwidth | ~$5–10/month |
| **Total** | **~$40–60/month** |

> Zabbix software is open-source (GPL v2) — no license cost.

---

## Import Proxmox Monitoring Template

The community template `template_proxmox-ve-rest-api-zabbix` monitors Proxmox via
REST API — no agent installation required on Proxmox.

**Template location (in this repo):**
```
community-templates/Virtualization/template_proxmox-ve-rest-api-zabbix/7.0/
  ├── README.md
  └── template_proxmox-ve-rest-api.yaml.yaml
```

### Step 1: Create Zabbix API User in Proxmox

1. Log in to Proxmox web UI
2. **Datacenter → Permissions → Users → Add**
   - User: `zabbix@pam`
   - Set a strong password
3. **Datacenter → Permissions → Add → User Permission**
   - Path: `/`
   - User: `zabbix@pam`
   - Role: `PVEAuditor`

### Step 2: Create API Token

1. **Datacenter → API Tokens → Add**
   - User: `zabbix@pam`
   - Token ID: `Zabbix`
   - Privilege Separation: **disabled**
2. Save the **Token Secret** — it is only shown once

### Step 3: Import Template into Zabbix

1. Go to **Configuration → Templates → Import**
2. Upload: `template_proxmox-ve-rest-api.yaml.yaml`
3. Click **Import**

### Step 4: Create a Zabbix Host

1. **Configuration → Hosts → Create host**
   - Host name: e.g. `proxmox01`
   - Template: `Template Proxmox VE REST API`
   - Group: `Linux servers` or create `Proxmox`
   - Leave **Interfaces empty** (REST API, no agent)

### Step 5: Set Required Macros

| Macro | Example | Description |
|-------|---------|-------------|
| `{$PVE_IP}` | `192.168.1.1` | Proxmox IP or hostname |
| `{$PVE_PORT}` | `8006` | Proxmox API port |
| `{$PVE_NODE}` | `pve` | Proxmox node name |
| `{$PVE_API_USER}` | `zabbix@pam` | API username |
| `{$PVE_API_TOKEN_ID}` | `Zabbix` | API token ID |
| `{$PVE_API_TOKEN}` | `<secret>` | API token secret (store as secret macro) |

### What Gets Monitored

| Discovery Rule | Description |
|---------------|-------------|
| `discover.nodes` | All Proxmox nodes |
| `discover.qemu` | QEMU/KVM virtual machines |
| `discover.lxc` | LXC containers |
| `discover.storage` | Storage pools and status |
| `discover.backup` | VZDUMP backup jobs |
| `discover.tasks` | Running tasks |
| `discover.users` | Users and expiration dates |

---

## MS Teams Alerting

### Step 1: Create Incoming Webhook in MS Teams

1. Open MS Teams → go to the target channel
2. Click **"..."** → **Connectors** (or **Workflows** in newer Teams)
3. Search **"Incoming Webhook"** → **Configure**
4. Name it `Zabbix Alerts` → click **Create**
5. Copy the **Webhook URL** → click **Done**

### Step 2: Configure Media Type in Zabbix

1. Go to **Alerts → Media types**
2. Search for **MS Teams** → click to edit
3. Set the parameter:

| Parameter | Value |
|-----------|-------|
| `teams_endpoint` | `<your Teams webhook URL>` |

4. Click **Test** to verify → check Teams channel for test message
5. Click **Update**

### Step 3: Assign Media Type to User

1. **Users → Users** → click your admin user
2. **Media** tab → **Add**

| Field | Value |
|-------|-------|
| Type | MS Teams |
| Send to | Your webhook URL |
| When active | `1-7,00:00-24:00` |
| Severity | Warning, Average, High, Disaster |

3. Click **Add** → **Update**

### Step 4: Create Trigger Action

1. **Alerts → Actions → Trigger actions → Create action**

**Action tab:**

| Field | Value |
|-------|-------|
| Name | `MS Teams Alerts` |
| Condition | Trigger severity >= Warning |

**Operations tab → Add:**

| Field | Value |
|-------|-------|
| Send to users | Admin |
| Send only to | MS Teams |

2. Also configure **Recovery operations** and **Acknowledgement operations** for
   resolved and acknowledged alert notifications.

### Alert Flow

```
Proxmox Event → Zabbix Trigger → Zabbix Action → MS Teams Webhook → Teams Channel
```

---

## Status Page with Uptime Kuma

Uptime Kuma is a free, open-source, self-hosted status page with a clean UI.

### Step 1: Install Docker on Azure VM

```bash
ssh azureuser@<public-ip>

curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker azureuser
newgrp docker
```

### Step 2: Deploy Uptime Kuma

```bash
docker run -d \
  --name uptime-kuma \
  -p 3001:3001 \
  -v uptime-kuma:/app/data \
  --restart unless-stopped \
  louislam/uptime-kuma:1
```

### Step 3: Open Port in Azure NSG

```bash
az vm open-port \
  --resource-group zabbix-rg \
  --name zabbix-server \
  --port 3001 \
  --priority 103
```

### Step 4: Access and Configure

Navigate to: `http://<public-ip>:3001`

1. Create an admin account on first visit
2. Click **Add New Monitor**

| Field | Value |
|-------|-------|
| Monitor Type | HTTP(s) |
| Friendly Name | Proxmox Web UI |
| URL | `https://<proxmox-ip>:8006` |
| Heartbeat Interval | 60 seconds |

3. Add monitors for each service (Proxmox nodes, VMs, etc.)

### Step 5: Configure Status Page

1. Go to **Status Page** tab → **New Status Page**
2. Add your monitors to the page
3. Set a custom domain (optional)
4. Enable **public access**
5. Share the status page URL with your team

### Step 6: Add MS Teams Notifications in Uptime Kuma

1. Go to **Settings → Notifications → Add Notification**
2. Select **Microsoft Teams**
3. Paste your **Teams Webhook URL**
4. Click **Test** → **Save**
5. Assign to your monitors

---

## Alternative Status Page Options

| Tool | Type | Cost | Best For |
|------|------|------|----------|
| **Uptime Kuma** | Self-hosted | Free | Small/medium teams |
| **Cachet** | Self-hosted | Free | Custom incident management |
| **Statping-ng** | Self-hosted | Free | Docker-based setups |
| **Zabbix Dashboard** | Built-in | Free | Internal teams (not public-facing) |
| **StatusPage.io** | SaaS | $29–99/month | Enterprise, polished public page |

### Zabbix Built-in Dashboard (No extra setup)

1. **Monitoring → Dashboard → Create dashboard**
2. Add widgets: Problems, Host availability, Graphs, Maps
3. **Share** → enable **Guest access**
4. Share the URL with your team

---

## Full Stack Summary

| Component | Tool | Hosting |
|-----------|------|---------|
| Infrastructure monitoring | Zabbix 7.0 | Azure VM |
| Proxmox metrics | REST API template | — |
| Alerting | MS Teams webhook | Teams channel |
| Status page | Uptime Kuma | Same Azure VM (Docker) |

---

## References

- Zabbix community templates repo: `community-templates/Virtualization/template_proxmox-ve-rest-api-zabbix/`
- Zabbix official docs: https://www.zabbix.com/documentation/7.0
- Uptime Kuma GitHub: https://github.com/louislam/uptime-kuma
