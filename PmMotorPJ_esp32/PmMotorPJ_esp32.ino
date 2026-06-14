/*
  ESP32-S3 Smart Window Controller (PmMotorPJ)
  
  Board: ESP32S3-WROOM-1
  Peripherals:
    - SG90 Servo Motor (Window Controller)
    - I2C LCD (16x2 or 20x4)
  MQTT Topics:
    - Publish: "tmp" (json), "humi" (json), "pm" (json)
    - Subscribe: "window/control"
*/

#include <WiFi.h>
#include <PubSubClient.h>
#include <ESP32Servo.h>        // Library: "ESP32Servo" by Kevin Harrington
#include <Wire.h>
#include <LiquidCrystal_I2C.h> // Library: "LiquidCrystal I2C" by Frank de Brabander

// ================= [핀 설정] =================
#define SERVO_PIN  18    // SG90 서보모터 신호선 (GPIO 18)
#define I2C_SDA    8    // LCD SDA (ESP32-S3 기본 혹은 원하는 핀으로 조정 가능)
#define I2C_SCL    9    // LCD SCL

// ================= [설정 변수] =================
// 1. 와이파이 설정
const char* ssid = "우진의 S25 Edge";
const char* password = "jh12091102!";

// 2. MQTT 서버 설정
// ★ 중요: Node.js 서버가 실행 중인 PC의 실제 IP 주소를 입력해야 합니다.
// (CMD에서 ipconfig를 입력하여 IPv4 주소를 확인하세요. 예: 192.168.0.15)
const char* mqtt_server = "10.116.143.106"; 
const int mqtt_port = 1883;

// ================= [객체 선언] =================
WiFiClient espClient;
PubSubClient client(espClient);
Servo windowServo;
LiquidCrystal_I2C lcd(0x27, 16, 2); // I2C 주소 0x27, 16열 2행 LCD

// ================= [서보모터 각도 설정] =================
const int CLOSE_ANGLE = 0;   // 창문 닫힘 각도
const int OPEN_ANGLE = 90;   // 창문 열림 각도 (필요에 따라 180 등으로 변경 가능)

unsigned long lastMsgTime = 0;
float currentTemp = 24.5;
float currentHumi = 55.0;
int currentPm = 00;
bool cleanerState = false; // 공기청정기 가상 상태 (false: OFF, true: ON)
String currentMode = "AUT"; // 현재 시스템 모드 (AUT 또는 SIM)
unsigned long commandActiveUntil = 0; // 명령어 화면 활성화 시간 (밀리초)

// ================= [함수 정의] =================

// 와이파이 연결 함수
void setup_wifi() {
  delay(10);
  Serial.println("\n------------------------------");
  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(ssid);

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Connecting WiFi...");

  WiFi.begin(ssid, password);

  int attempt = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    lcd.setCursor(attempt % 16, 1);
    lcd.print(".");
    attempt++;
    if (attempt > 30) { // 15초 이상 연결 안되면 재시도
      ESP.restart();
    }
  }

  Serial.println("\nWi-Fi connected!");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("WiFi Connected!");
  lcd.setCursor(0, 1);
  lcd.print(WiFi.localIP().toString());
  delay(2000);
}

bool needLcdUpdate = true; // LCD 초기 드로잉 예약
unsigned long lastLcdUpdateTime = 0;

// MQTT 수신 콜백 함수 (서버가 window/control, window/mode, pm, tmp, humi 토픽을 발행하면 실행됨)
void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");

  String message = "";
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  Serial.println(message);

  // 0. 시스템 모드(mode) 수신 처리
  if (String(topic) == "window/mode") {
    if (message == "auto") {
      currentMode = "AUT";
    } else if (message == "simulation") {
      currentMode = "SIM";
    }
    needLcdUpdate = true; // LCD 갱신 플래그
    return;
  }

  // 1. 미세먼지(pm) 수치 수신 처리
  if (String(topic) == "pm") {
    // message 예시: {"pm": 120}
    int colonIndex = message.indexOf(':');
    int braceIndex = message.indexOf('}');
    if (colonIndex != -1 && braceIndex != -1) {
      String pmStr = message.substring(colonIndex + 1, braceIndex);
      pmStr.trim();
      currentPm = pmStr.toInt();
      needLcdUpdate = true; // LCD 갱신 플래그
    }
    return;
  }

  // 2. 온도(tmp) 수치 수신 처리
  if (String(topic) == "tmp") {
    // message 예시: {"tmp": 24.5}
    int colonIndex = message.indexOf(':');
    int braceIndex = message.indexOf('}');
    if (colonIndex != -1 && braceIndex != -1) {
      String tempStr = message.substring(colonIndex + 1, braceIndex);
      tempStr.trim();
      currentTemp = tempStr.toFloat();
      needLcdUpdate = true; // LCD 갱신 플래그
    }
    return;
  }

  // 3. 습도(humi) 수치 수신 처리
  if (String(topic) == "humi") {
    // message 예시: {"humi": 55.0}
    int colonIndex = message.indexOf(':');
    int braceIndex = message.indexOf('}');
    if (colonIndex != -1 && braceIndex != -1) {
      String humiStr = message.substring(colonIndex + 1, braceIndex);
      humiStr.trim();
      currentHumi = humiStr.toFloat();
      needLcdUpdate = true; // LCD 갱신 플래그
    }
    return;
  }

  // 4. 창문 제어 메시지 수신 처리 (window/control)
  if (String(topic) == "window/control") {
    // LCD 첫째 줄 지우고 이벤트 표시
    lcd.setCursor(0, 0);
    lcd.print("                ");
    lcd.setCursor(0, 0);
    lcd.print("Cmd: " + message);

    if (message == "auto_close") {
      Serial.println("Action: Auto Closing window (Servo to 0 deg) & Cleaner ON");
      windowServo.write(CLOSE_ANGLE);
      cleanerState = true; // 자동 닫힘 시 공기청정기 가동
      
      lcd.setCursor(0, 1);
      lcd.print("                ");
      lcd.setCursor(0, 1);
      lcd.print("AUTO CLSD/CLN ON");
    } 
    else if (message == "manual_close") {
      Serial.println("Action: Manual Closing window (Servo to 0 deg) & Cleaner ON");
      windowServo.write(CLOSE_ANGLE);
      cleanerState = true; // 수동 닫힘(warning 포함) 시에도 공기청정기 가동
      
      lcd.setCursor(0, 1);
      lcd.print("                ");
      lcd.setCursor(0, 1);
      lcd.print("MANU CLSD/CLN ON");
    }
    else if (message == "auto_open" || message == "manual_open") {
      Serial.println("Action: Opening window (Servo to 90 deg) & Cleaner OFF");
      windowServo.write(OPEN_ANGLE);
      cleanerState = false; // 창문 열림 시 공기청정기 꺼짐

      lcd.setCursor(0, 1);
      lcd.print("                ");
      lcd.setCursor(0, 1);
      lcd.print(message == "auto_open" ? "AUTO OPEN/CLN OF" : "MANU OPEN/CLN OF");
    }
    
    // LCD 명령어 화면 표시 유지용 타임스탬프 설정 (4초간 대기)
    commandActiveUntil = millis() + 4000;
    needLcdUpdate = true;
  }
}

unsigned long lastReconnectAttempt = 0;

// MQTT 재연결 함수 (비차단형 - non-blocking)
bool reconnect() {
  Serial.print("Attempting MQTT connection...");
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("MQTT Connecting...");

  // 클라이언트 ID 설정
  if (client.connect("ESP32S3_Window_Client")) {
    Serial.println("connected to MQTT Broker!");
    lcd.setCursor(0, 1);
    lcd.print("MQTT Connected!");
    delay(500);

    // 제어 및 센서 토픽 구독 등록
    client.subscribe("window/control");
    client.subscribe("window/mode");
    client.subscribe("pm");
    client.subscribe("tmp");
    client.subscribe("humi");
    
    // LCD 화면 초기화 예약
    needLcdUpdate = true;
    return true;
  }
  return false;
}

// LCD에 현재 상태 표시 함수
void updateLCDDisplay() {
  lcd.clear();
  // 첫째 줄: T: 24.5C H: 55% [AUT]/[SIM]
  lcd.setCursor(0, 0);
  lcd.print("T:");
  lcd.print(currentTemp, 1);
  lcd.print("C H:");
  lcd.print(currentHumi, 0);
  lcd.print("%");
  lcd.setCursor(13, 0);
  lcd.print(currentMode);

  // 둘째 줄: PM:35  CLN:OFF
  lcd.setCursor(0, 1);
  lcd.print("PM:");
  lcd.print(currentPm);
  lcd.setCursor(9, 1);
  lcd.print("CLN:");
  lcd.print(cleanerState ? "ON " : "OFF");
}

void setup() {
  Serial.begin(115200);

  // I2C 초기화 (ESP32-S3 SDA, SCL 핀 명시)
  Wire.begin(I2C_SDA, I2C_SCL);

  // LCD 초기화
  lcd.init();
  lcd.backlight();
  lcd.clear();

  // 서보모터 초기화
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);
  windowServo.setPeriodHertz(50); // SG90 서보모터 주파수 50Hz
  windowServo.attach(SERVO_PIN, 500, 2400); // SG90의 최소/최대 펄스 폭 설정
  windowServo.write(CLOSE_ANGLE); // 초기 상태 닫힘

  // 와이파이 & MQTT 설정
  setup_wifi();
  
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
  client.setKeepAlive(30); // 핫스팟 환경 등 불안정한 네트워크를 위한 keep-alive 타임아웃 증가 (초 단위)
}

void loop() {
  if (!client.connected()) {
    unsigned long now = millis();
    if (now - lastReconnectAttempt > 5000) {
      lastReconnectAttempt = now;
      if (reconnect()) {
        lastReconnectAttempt = 0;
      } else {
        Serial.print("failed, rc=");
        Serial.print(client.state());
        Serial.println(" will try again in 5 seconds");
        lcd.setCursor(0, 1);
        lcd.print("Failed. Retry...");
      }
    }
  } else {
    client.loop();
  }

  // LCD 갱신 처리 (최소 500ms 간격을 두고 갱신하여 I2C 통신 병목 및 CPU 점유 방지)
  // 단, 명령어 활성화 화면(commandActiveUntil) 표시 중일 때는 완료 전까지 갱신을 지연합니다.
  if (needLcdUpdate) {
    unsigned long now = millis();
    if (now > commandActiveUntil && now - lastLcdUpdateTime > 500) {
      lastLcdUpdateTime = now;
      updateLCDDisplay();
      needLcdUpdate = false;
    }
  }
}
