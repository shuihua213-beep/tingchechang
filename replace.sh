#!/bin/bash
# ============================================================
# 统一环境变量配置生成脚本 (v3.0+)
# ============================================================
# 用法:
#   ./replace.sh                          # 交互式询问各项配置
#   ./replace.sh env                      # 使用当前环境变量生成 .env
#   ./replace.sh gen ZK_IP DB_IP DB_USER DB_PWD REDIS_IP REDIS_PWD MONGO_IP MONGO_USER MONGO_PWD FASTDFS_IP
# ============================================================

ENV_FILE=".env"

gen_env() {
    cat > ${ENV_FILE} <<EOF
# ========== Zookeeper / Dubbo 注册中心 ==========
ZK_HOST=${ZK_HOST:-127.0.0.1}
ZK_PORT=2181

# ========== MySQL 数据库 ==========
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=3306
MYSQL_DB=${MYSQL_DB:-caifeng}
MYSQL_USER=${MYSQL_USER:-caifeng}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-caifeng}

# ========== Redis 缓存 ==========
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_PASSWORD:-}
REDIS_DATABASE=0

# ========== MongoDB 文档数据库 ==========
MONGODB_URI=${MONGODB_URI:-mongodb://caifeng:caifeng@127.0.0.1:27017/caifeng}
MONGODB_DATABASE=${MONGODB_DATABASE:-caifeng}

# ========== FastDFS 文件存储 ==========
FASTDFS_TRACKER_SERVERS=${FASTDFS_TRACKER_SERVERS:-127.0.0.1:22122}

# ========== OAuth2 认证 ==========
AUTH_TOKEN_VALIDITY_SECONDS=1200
AUTH_CLIENT_ID=oauth2_client_id
AUTH_CLIENT_SECRET=oauth2_client_secret
AUTH_COOKIE_DOMAIN=xuecheng.com
AUTH_COOKIE_MAX_AGE=-1

# ========== 加密密钥库 ==========
ENCRYPT_KEYSTORE_LOCATION=classpath:/xc.keystore
ENCRYPT_KEYSTORE_SECRET=xuechengkeystore
ENCRYPT_KEYSTORE_ALIAS=xckey
ENCRYPT_KEYSTORE_PASSWORD=xuecheng

# ========== Authority API ==========
AUTHORITY_API_URL=http://127.0.0.1:16007

# ========== 海康设备 (forward) ==========
HK_BASE_URL=http://192.168.3.19
HK_LOCAL_IP=192.168.3.252
HK_DEVICE_IP=192.168.3.249
HK_PORT=8000
HK_USERNAME=admin
HK_PASSWORD=abcde12345

# ========== 大华设备 (forward-dh) ==========
DH_CAMERA_IP=192.168.3.200
DH_CAMERA_SERVER=http://192.168.3.19:8089
DH_CAMERA_PORT=37777
DH_CAMERA_USERNAME=admin
DH_CAMERA_PASSWORD=admin123
DH_CAMERA_UUID=6F01FFAPAJ65519
DH_LED_IP=192.168.3.250
DH_LED_PORT=5005
DH_LED_UUID=6F01FFAPAJ65519
EOF
    echo "[OK] .env 文件已生成: ${ENV_FILE}"
}

interactive() {
    read -p "ZK 地址 [127.0.0.1]: " ZK_HOST
    ZK_HOST=${ZK_HOST:-127.0.0.1}
    read -p "MySQL 地址 [127.0.0.1]: " MYSQL_HOST
    MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
    read -p "MySQL 用户名 [caifeng]: " MYSQL_USER
    MYSQL_USER=${MYSQL_USER:-caifeng}
    read -p "MySQL 密码 [caifeng]: " MYSQL_PASSWORD
    MYSQL_PASSWORD=${MYSQL_PASSWORD:-caifeng}
    read -p "Redis 地址 [127.0.0.1]: " REDIS_HOST
    REDIS_HOST=${REDIS_HOST:-127.0.0.1}
    read -p "Redis 密码 [空]: " REDIS_PASSWORD
    read -p "MongoDB 地址 [127.0.0.1]: " MONGO_IP
    read -p "MongoDB 用户名 [caifeng]: " MONGO_USER
    MONGO_USER=${MONGO_USER:-caifeng}
    read -p "MongoDB 密码 [caifeng]: " MONGO_PWD
    MONGO_PWD=${MONGO_PWD:-caifeng}
    MONGODB_URI="mongodb://${MONGO_USER}:${MONGO_PWD}@${MONGO_IP:-127.0.0.1}:27017/caifeng"
    read -p "FastDFS Tracker 地址 [127.0.0.1:22122]: " FASTDFS_TRACKER_SERVERS
    FASTDFS_TRACKER_SERVERS=${FASTDFS_TRACKER_SERVERS:-127.0.0.1:22122}
    gen_env
}

gen_from_args() {
    ZK_HOST=${1:-127.0.0.1}
    MYSQL_HOST=${2:-127.0.0.1}
    MYSQL_USER=${3:-caifeng}
    MYSQL_PASSWORD=${4:-caifeng}
    REDIS_HOST=${5:-127.0.0.1}
    REDIS_PASSWORD=${6:-}
    MONGO_IP=${7:-127.0.0.1}
    MONGO_USER=${8:-caifeng}
    MONGO_PWD=${9:-caifeng}
    MONGODB_URI="mongodb://${MONGO_USER}:${MONGO_PWD}@${MONGO_IP}:27017/caifeng"
    FASTDFS_TRACKER_SERVERS=${10:-127.0.0.1:22122}
    gen_env
}

case "${1}" in
    env)
        gen_env
        ;;
    gen)
        gen_from_args "$@"
        ;;
    *)
        interactive
        ;;
esac
