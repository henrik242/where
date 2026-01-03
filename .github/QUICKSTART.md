# Quick Start: GitHub Actions Setup

## 1. Generate SSH Key

```bash
ssh-keygen -t ed25519 -C "github-actions@where" -f ~/.ssh/where-deploy
ssh-copy-id -i ~/.ssh/where-deploy.pub user@where.synth.no
```

## 2. Add GitHub Secrets

Go to: **Settings → Secrets and variables → Actions → New repository secret**

Add these three secrets:

| Name | Value |
|------|-------|
| `SSH_PRIVATE_KEY` | Contents of `~/.ssh/where-deploy` |
| `SERVER_HOST` | `where.synth.no` |
| `SSH_USER` | Your SSH username |

## 3. Prepare Server

```bash
# SSH to server
ssh user@where.synth.no

# Create directory
sudo mkdir -p /opt/where-server
sudo chown $USER:$USER /opt/where-server

# Install Bun
curl -fsSL https://bun.sh/install | bash
source ~/.bashrc
```

## 4. Push to GitHub

```bash
git add .
git commit -m "Add GitHub Actions workflows"
git push origin main
```

## 5. Monitor Deployment

Go to **Actions** tab in GitHub to see the workflows running.

## Server URLs

- **Web Interface**: https://where.synth.no
- **API**: https://where.synth.no/api/tracks
- **WebSocket**: wss://where.synth.no/ws

## Verify Deployment

```bash
ssh user@where.synth.no 'sudo systemctl status where-server'
curl https://where.synth.no/api/tracks
```

Done! 🎉

