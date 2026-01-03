# GitHub Actions Setup

This repository uses GitHub Actions for CI/CD automation.

## Workflows

### 1. Build Android App (`build-android.yml`)

Automatically builds the Android app when:
- Code is pushed to `main` or `develop` branches
- Pull requests are opened against `main`
- Manually triggered via workflow_dispatch

**What it does:**
- Sets up JDK 21
- Builds debug and release APKs
- Runs unit tests
- Uploads APK artifacts (retained for 30 days)
- Uploads test results (retained for 14 days)

**Artifacts:**
- `app-debug.apk` - Debug build for testing
- `app-release-unsigned.apk` - Release build (needs signing)
- `test-results/` - Unit test reports

### 2. Deploy Server (`deploy-server.yml`)

Automatically deploys the tracking server when:
- Code is pushed to `main` branch with server changes
- Manually triggered via workflow_dispatch

**What it does:**
- Connects to server via SSH
- Installs Bun if not present
- Syncs server files to `/opt/where-server`
- Installs dependencies
- Sets up systemd service
- Restarts the server
- Verifies deployment

## Required Secrets

Add these secrets in your GitHub repository settings (Settings → Secrets and variables → Actions):

### For Server Deployment

1. **`SSH_PRIVATE_KEY`**
   - Your SSH private key for server access
   - Generate with: `ssh-keygen -t ed25519 -C "github-actions@where"`
   - Add public key to server's `~/.ssh/authorized_keys`

2. **`SERVER_HOST`**
   - Your server hostname or IP
   - Example: `where.synth.no` or `192.168.1.100`

3. **`SSH_USER`**
   - SSH username for server access
   - Example: `deploy` or `ubuntu`

## Setup Instructions

### 1. Generate SSH Key for Deployment

```bash
# On your local machine
ssh-keygen -t ed25519 -C "github-actions@where" -f ~/.ssh/where-deploy

# Copy public key to server
ssh-copy-id -i ~/.ssh/where-deploy.pub user@where.synth.no

# Get private key content for GitHub secret
cat ~/.ssh/where-deploy
```

### 2. Add Secrets to GitHub

1. Go to your repository on GitHub
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret:
   - Name: `SSH_PRIVATE_KEY`, Value: (paste private key content)
   - Name: `SERVER_HOST`, Value: `where.synth.no`
   - Name: `SSH_USER`, Value: `your-username`

### 3. Server Preparation

On your server (`where.synth.no`):

```bash
# Create deployment directory
sudo mkdir -p /opt/where-server
sudo chown $USER:$USER /opt/where-server

# Install Bun (if not already installed)
curl -fsSL https://bun.sh/install | bash

# Verify Bun is in PATH
source ~/.bashrc
bun --version
```

### 4. Nginx Configuration (Optional)

If using nginx as reverse proxy:

```nginx
server {
    server_name where.synth.no;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /ws {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
    }

    # SSL configuration (after certbot)
    listen 443 ssl;
    ssl_certificate /etc/letsencrypt/live/where.synth.no/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/where.synth.no/privkey.pem;
}

server {
    listen 80;
    server_name where.synth.no;
    return 301 https://$server_name$request_uri;
}
```

### 5. SSL Certificate (Optional)

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d where.synth.no
```

## Manual Deployment

You can also trigger deployments manually:

1. Go to **Actions** tab in your repository
2. Select **Deploy Server** workflow
3. Click **Run workflow**
4. Select branch and click **Run workflow**

## Monitoring Deployments

### View Workflow Runs

1. Go to **Actions** tab
2. Click on a workflow run to see details
3. Click on job steps to see logs

### Check Server Status

```bash
# SSH to server
ssh user@where.synth.no

# Check service status
sudo systemctl status where-server

# View logs
sudo journalctl -u where-server -f

# Check if server is responding
curl http://localhost:3000/api/tracks
```

## Troubleshooting

### Build Failures

- Check Gradle cache issues: Re-run the workflow
- Java version mismatch: Ensure JDK 21 is specified
- Test failures: Review test results artifact

### Deployment Failures

**SSH Connection Issues:**
```bash
# Verify SSH key is correct
ssh -i ~/.ssh/where-deploy user@where.synth.no

# Check known_hosts
ssh-keyscan where.synth.no
```

**Bun Installation Issues:**
```bash
# Manually install Bun on server
ssh user@where.synth.no
curl -fsSL https://bun.sh/install | bash
source ~/.bashrc
```

**Service Start Failures:**
```bash
# Check service logs
sudo journalctl -u where-server -n 50

# Check permissions
ls -la /opt/where-server

# Verify Bun path
which bun
```

**Port Already in Use:**
```bash
# Find process using port 3000
sudo lsof -i :3000

# Kill if needed
sudo kill <PID>
```

## Security Notes

⚠️ **Important:**
- Never commit SSH private keys to the repository
- Use GitHub Secrets for all sensitive data
- Restrict SSH key permissions: `chmod 600 ~/.ssh/where-deploy`
- Consider using a dedicated deployment user with limited privileges
- Regularly rotate SSH keys
- Use firewall rules to restrict SSH access

## Advanced Configuration

### Custom Port

To deploy to a different port:

1. Update `server/.env`:
   ```
   PORT=8080
   ```

2. Update systemd service file with environment variable

3. Update nginx proxy_pass to new port

### Multiple Environments

Create separate workflows for staging/production:

```yaml
# .github/workflows/deploy-staging.yml
on:
  push:
    branches: [ develop ]
# Use different secrets: STAGING_SERVER_HOST, etc.
```

### Slack Notifications

Add notification step:

```yaml
- name: Notify Slack
  if: always()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

## License

Same as the main Where app

