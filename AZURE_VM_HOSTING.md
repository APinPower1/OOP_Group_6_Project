# Host Student Portal on Azure VM
Quick steps for sem3 demo / viva defense.

## Prerequisites
- Azure VM (Ubuntu 22.04 LTS, Standard_D2s_v3 or B2s)
- Open port 22 (SSH), 8080 (Spring Boot), 3306 (MySQL — restrict to VM only)
- Domain / IP for access

## 1. VM Setup (Azure Portal / CLI)
```bash
# Create (Azure CLI)
az vm create \
  --resource-group myRG \
  --name student-portal-vm \
  --image Ubuntu2204 \
  --size Standard_B2s \
  --public-ip-sku Standard \
  --ssh-key-values ~/.ssh/id_rsa.pub

# Open ports
az vm open-port --port 8080 --resource-group myRG --name student-portal-vm
az vm open-port --port 22 --resource-group myRG --name student-portal-vm
```

## 2. SSH & Install
```bash
ssh azureuser@<VM-IP>
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk mysql-server-8.4 gradle git
sudo systemctl enable mysql --now
```

## 3. Database Setup
```bash
sudo mysql -u root -p < /path/to/backend/src/main/resources/db/migration/V1__initial_schema.sql
# Or create user + DB from docs/LOCAL_DEVELOPMENT.md
```

## 4. Deploy Backend (Spring Boot JAR)
```bash
# On your machine — build
./gradlew :backend:bootJar

# Copy to VM
scp backend/build/libs/*.jar azureuser@<VM-IP>:~/app.jar
scp backend/src/main/resources/application.properties azureuser@<VM-IP>:~/

# On VM — run (production profile)
nohup java -jar ~/app.jar --spring.profiles.active=prod > ~/app.log 2>&1 &
curl http://localhost:8080/health   # verify
```

## 5. Frontend (Swing Client)
```bash
# Build client JAR
./gradlew :client:jar
scp client/build/libs/*.jar azureuser@<VM-IP>:~/client.jar

# On VM — run locally for demo (connects to VM IP:8080)
java -cp ~/client.jar edu.mec.studentportal.client.Main
# Configure client-config.properties with VM_IP:8080
```

## 6. Make Persistent (systemd)
```bash
# /etc/systemd/system/student-portal.service
[Unit]
Description=Student Portal Spring Boot
After=network.target

[Service]
User=azureuser
WorkingDirectory=/home/azureuser
ExecStart=/usr/bin/java -jar /home/azureuser/app.jar --spring.profiles.active=prod
Restart=always

[Install]
WantedBy=multi-user.target

sudo systemctl daemon-reload
sudo systemctl enable student-portal
sudo systemctl start student-portal
```

## 7. Access
- API: `http://<VM-IP>:8080/api/events`
- Client: run Swing jar locally, point to VM IP
- MySQL: only from VM (do NOT expose 3306 to public)

## Notes for Viva
- Show `Systemctl status student-portal`
- Show `curl` to `/api/events` returning real data
- Explain architecture: Swing → REST → Spring Boot → MySQL
- Mention security: JWT auth, role checks, parameterized SQL, SELECT FOR UPDATE
