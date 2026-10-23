#!/usr/bin/env bash
# 새 Ubuntu VM(Oracle Cloud Always Free 기준)에서 한 번만 실행: Docker 설치, 스왑, 80/443 방화벽 열기.
#   bash deploy/server-setup.sh
set -euo pipefail

# 1) 스왑 2GB - 빌드(maven + npm) 중 메모리 부족으로 죽지 않게. 이미 있으면 건너뜀
if ! swapon --show | grep -q .; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# 2) Docker + compose 플러그인
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# 3) OS 방화벽 - Oracle의 Ubuntu 이미지는 iptables가 22번 외에는 REJECT한다.
#    REJECT 규칙 앞에 80/443을 끼워 넣는다 (클라우드 콘솔의 Security List도 따로 열어야 함 - DEPLOYMENT.md 7)
reject_line=$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" {print $1; exit}')
for port in 80 443; do
  if ! sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; then
    if [ -n "$reject_line" ]; then
      sudo iptables -I INPUT "$reject_line" -p tcp --dport "$port" -m state --state NEW -j ACCEPT
    else
      sudo iptables -A INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT
    fi
  fi
done
if ! sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null && [ -n "$reject_line" ]; then
  sudo iptables -I INPUT "$reject_line" -p udp --dport 443 -j ACCEPT
fi
if command -v netfilter-persistent >/dev/null; then
  sudo netfilter-persistent save
fi

echo "완료. docker 그룹 적용을 위해 한 번 로그아웃 후 다시 접속하세요."
