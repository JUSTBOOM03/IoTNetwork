# OpenAPI 및 MQTT 기반 실시간 대기환경 연동형 스마트 윈도우 & 가상 공기청정기 제어 시스템

본 프로젝트는 공공 OpenAPI의 실시간 기상 상태 정보를 활용하여 창문을 자동으로 여닫고 가상 공기청정기(CLN)의 기동을 연동 제어하는 스마트 홈 IoT 프로토타입 시스템입니다.

- **과목명**: IoT네트워크 (한림대학교 소프트웨어융합대학)
- **개발자**: 최우진 (학번: 20225250)

---

## 🏗️ 시스템 아키텍처 및 구성 레이어

```
[OpenAPI] (기상청/에어코리아)
    │ (HTTP GET / XML)
    ▼
[Java Client] (PmMotorPJ) ───(Publish: tmp, humi, pm)───┐
    │                                                    │
    ▼                                                    ▼
[MQTT Broker] (Mosquitto) ◀────────────────────────▶ [Node.js Web Server] (Express, Socket.io)
    │ (Subscribe / Publish)                              │ (Read/Write)
    ▼                                                    ▼
[ESP32S3 Device] (Servo, LCD)                        [MongoDB] (Database: IoTDB)
                                                         │ (Real-time socket connection)
                                                         ▼
                                                     [Web Dashboard] (PmMotorPJ.html)
```

1. **데이터 수집 레이어 (Java Client)**: 기상청 단기예보(온도/습도) 및 한국환경공단(미세먼지) OpenAPI를 연동하여 데이터를 수집합니다. 트래픽 Throttling(429 에러)을 예방하기 위해 1시간 단위 캐싱 및 Fallback 기동 로직이 탑재되어 있습니다.
2. **비동기 메시징 레이어 (MQTT Broker)**: Mosquitto Broker를 활용해 분산 클라이언트(자바, 웹 서버, ESP32 장치) 간의 메시지를 실시간으로 퍼블리싱 및 구독(Pub/Sub)합니다.
3. **웹 및 서버 레이어 (Node.js & MongoDB)**: 브로커를 구독하여 데이터를 수신하고 MongoDB에 적재(Logging)합니다. Socket.io를 통해 대시보드 화면에 3초 간격으로 실시간 푸시하며, 가상 시뮬레이션 및 수동 제어 이벤트를 중계합니다.
4. **물리 액추에이터 레이어 (ESP32-S3)**: Wi-Fi 네트워크를 경유하여 MQTT 브로커와 연결되며, I2C 1602 LCD 화면에 환경 정보를 출력하고 서보모터(SG90)를 0도(닫힘)/90도(열림)로 회전해 창문을 물리적으로 구동시킵니다.

---

## 📁 프로젝트 폴더 구조

```
.
├── IoT_LastProject/          # OpenAPI 기상 데이터 수집용 Java 클라이언트 (Eclipse)
│   ├── src/week10/           # 데이터 파싱, 캐싱 및 MQTT Publish/Subscribe 자바 소스
│   ├── .project              # Eclipse 프로젝트 설정 파일
│   └── .classpath            # Eclipse 빌드 패스 설정 파일
│
├── IoT_server/               # Express 기반 Node.js 웹 서버 및 대시보드 페이지
│   ├── bin/PmMotorPJ         # 서버 엔트리포인트 (소켓 통신, DB 저장, 제어 로직 내장)
│   ├── public/               # 웹 대시보드 페이지 (HTML, CSS, JS 및 UI 에셋)
│   ├── app.js                # Express 애플리케이션 초기 설정 파일
│   └── package.json          # Node.js 의존성 패키지 관리 파일
│
├── PmMotorPJ_esp32/          # ESP32-S3 하드웨어 제어 및 센서 통신용 아두이노 스케치
│   └── PmMotorPJ_esp32.ino   # Wi-Fi / MQTT 연결, LCD 갱신 및 서보모터 구동 코드
│
├── README.md                 # 프로젝트 가이드 명세서 (본 파일)
└── .gitignore                # 깃 관리 제외 파일 설정
```

---

## 🛠️ 사용 기술 및 환경 (Stack)

* **Hardware Board**: ESP32-S3 (ESP32S3-WROOM-1)
* **Peripherals**: SG90 Servo Motor (GPIO 4), I2C LCD 1602 (SDA: GPIO 8, SCL: GPIO 9, Address: 0x27)
* **Backend**: Node.js v18+, Express.js, Socket.io, MongoDB v6.0+
* **Messaging Broker**: Mosquitto MQTT v2.0+
* **Client Compiler**: JavaSE-1.8 (Eclipse IDE), Arduino IDE 2.x
* **Dependencies**: Jsoup (Java XML Parser), Paho MQTT Client (Java), PubSubClient (Arduino), LiquidCrystal_I2C (Arduino)

---

## 🚀 구동 및 가동 방법

1. **Mosquitto MQTT 브로커 실행**
   * 설정 파일(`mosquitto.conf`) 내에 외부 대역 접속 허용 및 익명 접속 설정을 기입하고 실행합니다.
   * `mosquitto -c mosquitto.conf`

2. **MongoDB 가동**
   * 로컬 MongoDB 서비스를 구동합니다. (기본 포트: 27017)

3. **Node.js 웹 서버 실행**
   * `IoT_server` 폴더 내로 이동해 의존 패키지를 다운로드한 뒤 구동합니다.
   * `cd IoT_server`
   * `npm install`
   * `npm start` (또는 `node bin/PmMotorPJ`)

4. **Java Client 실행**
   * Eclipse에서 `IoT_LastProject` 프로젝트를 불러온 뒤 `PmMotorPJ.java` 파일을 실행(Run)합니다.
   * 1시간 주기로 기상청 API 데이터를 갱신하여 MQTT 토픽으로 데이터를 발행하기 시작합니다.

5. **ESP32-S3 아두이노 펌웨어 업로드**
   * `PmMotorPJ_esp32/PmMotorPJ_esp32.ino` 파일을 아두이노 IDE로 열어 Wi-Fi 공유기 SSID/PW와 PC의 IP 주소를 적절히 수정한 뒤 업로드합니다.
   * 와이파이 접속 완료 및 MQTT Broker와 바인딩되면 LCD가 갱신되고 서보모터가 닫힘(0도) 상태로 초기 대기합니다.

6. **웹 모니터링 및 시뮬레이션 동작 테스트**
   * 웹 브라우저에서 `http://localhost:3000`에 접속합니다.
   * 자동 제어 모드와 가상 시뮬레이션 모드를 전환하며 동작을 확인합니다.
