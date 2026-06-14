package week10;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class PmMotorPJ implements MqttCallback{
	static MqttClient sampleClient;
		
    public static void main(String[] args) {
        System.out.println("IoT 네트워크 기말 프로젝트");
    	PmMotorPJ obj = new PmMotorPJ();
    	obj.run();
    }
    
    static String cached_pm = "0";
    static String cached_tmp = "0";
    static String cached_humi = "0";
    static long lastFetchTime = 0;
    static final long FETCH_INTERVAL = 60000 * 60; // 1시간 (3,600,000ms)
    static String currentMode = "auto";

    public void run() {
		connectBroker();
    	try {
    		sampleClient.subscribe("led");
    		sampleClient.subscribe("window/mode");
    	} catch (MqttException e) {
    		e.printStackTrace();
    	}
    	    	
    	while(true) {    		
        	try {
        		long currentTime = System.currentTimeMillis();
        		// 5분마다 한 번씩만 API를 호출해서 변수(캐시)에 저장
        		if (currentTime - lastFetchTime >= FETCH_INTERVAL || lastFetchTime == 0) {
        			System.out.println("=== 기상청/미세먼지 API 데이터 갱신 중 ===");
        			try {
	        			String temp_pm = get_pm_data();
	                	String[] weather_data = get_weather_data();
	                	
	                	// API에서 값을 정상적으로 파싱해온 경우에만 업데이트 (빈 값 방지)
	                	if(weather_data[0] != null && !weather_data[0].isEmpty()) cached_tmp = weather_data[0];
	                	if(weather_data[1] != null && !weather_data[1].isEmpty()) cached_humi = weather_data[1];
	                	if(temp_pm != null && !temp_pm.isEmpty()) cached_pm = temp_pm; 
	                	
	                	lastFetchTime = currentTime;
	                	System.out.println("=== API 갱신 완료 (미세먼지: " + cached_pm + ") ===");
        			} catch (Exception apiEx) {
        				System.out.println("API 호출 에러 (이전 데이터를 유지합니다): " + apiEx.getMessage());
        			}
        		}
            	 
        		// MQTT 퍼블리싱은 저장된 변수 값으로 5초마다 계속 전송 (웹페이지 실시간 모니터링용)
        		// 빈 값("")일 경우 JSON 에러를 방지하기 위해 "0"으로 전송
            	publish_data("tmp", "{\"tmp\": "+ (cached_tmp.isEmpty() ? "0" : cached_tmp) +"}");
            	publish_data("humi", "{\"humi\": "+ (cached_humi.isEmpty() ? "0" : cached_humi) +"}");
            	if ("auto".equals(currentMode)) {
            		publish_data("pm", "{\"pm\": "+ (cached_pm.isEmpty() ? "0" : cached_pm) +"}");
            	}
            	
				Thread.sleep(10000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				try {
	    			sampleClient.disconnect();	    	        
	    		} catch (MqttException e) {    		
	    			e.printStackTrace();
	    		}
				e1.printStackTrace();
				System.out.println("Disconnected");
    	        System.exit(0);
			}        	    	
    	}
	}
    
    public void connectBroker() {
        String broker       = "tcp://10.116.143.106:1883"; // Broker Server IP
        String clientId     = "practice"; // Client ID
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            sampleClient = new MqttClient(broker, clientId, persistence); // Initialization of MQTT Client
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: "+broker);
            sampleClient.connect(connOpts);
            sampleClient.setCallback(this); // Callback option 추가
            System.out.println("Connected");
        } catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        }
    }
    
    public static void publish_data(String topic_input, String data) {
        String topic        = topic_input;
        int qos             = 0;
        try {
            
            sampleClient.publish(topic, data.getBytes(), qos, false);
            
        } catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        }
    }
    
    public static String[] get_weather_data() {
    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.MINUTE, -40); // 40분 전으로 보정하여 최신 날씨 정보 확보
    	Date targetDate = cal.getTime();
    	SimpleDateFormat d_format = new SimpleDateFormat("yyyyMMdd"); 
    	SimpleDateFormat t_format = new SimpleDateFormat("HH"); 
    	String date = d_format.format(targetDate);    	
    	String time = t_format.format(targetDate);    	
    	String url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
    			+ "?serviceKey=1e181a891318026c035b8e44126be2bf2d1b9d8487c0a6cccae2a486fe6819d5"
    			+ "&pageNo=1&numOfRows=1000"
    			+ "&dataType=XML"
    			+ "&base_date="+ date
    			+ "&base_time=" + time + "00"
    			+ "&nx=55"
    			+ "&ny=127";
    	    	
		String temp = "";
		String humi = "";
				
    	Document doc = null;
		
		// 2. 파싱
		try {
			doc = Jsoup.connect(url).get();
		} catch (IOException e) {
			System.out.println("[알림] 기상청 API 호출 횟수 초과 또는 네트워크 지연 발생 (기본 온도 24.5C, 습도 55%로 대체 작동합니다.)");
			String[] fallback = {"24.5", "55"};
			return fallback;
		}
		//System.out.println(doc);
		
		Elements elements = doc.select("item");
		for (Element e : elements) {
			if (e.select("category").text().equals("T1H")) {
				temp = e.select("obsrValue").text();
			}
			if (e.select("category").text().equals("REH")) {
				humi = e.select("obsrValue").text();
			}
		}
		String[] out = {temp, humi};
    	return out;
    }
    
    public static String get_pm_data() {
    	String baseUrl = "http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty";
		String value = "";
    	Document doc = null;
		
		try {
			doc = Jsoup.connect(baseUrl)
					.data("serviceKey", "1e181a891318026c035b8e44126be2bf2d1b9d8487c0a6cccae2a486fe6819d5")
					.data("returnType", "xml")
					.data("numOfRows", "100")
					.data("pageNo", "1")
					.data("sidoName", "강원")
					.data("ver", "1.0")
					.get();
		} catch (IOException e) {
			System.out.println("[알림] 미세먼지 API 호출 횟수 초과(429) 또는 네트워크 지연 발생 (기본 미세먼지 35로 대체 작동합니다.)");
			return "35";
		}
		
		if (doc != null) {
			Elements elements = doc.select("item");
			System.out.println("[디버그] 조회된 대기오염 측정소 개수: " + elements.size());
			for (Element e : elements) {
				// 구버전 Jsoup 호환을 위해 태그명을 소문자로 검색합니다.
				String station = e.select("stationname").text();
				// 춘천 지역의 공식 측정소 명칭인 "중앙로(강원)"을 매칭하기 위해 "중앙로" 포함 여부를 검사합니다.
				if (station.contains("중앙로")) {
					value = e.select("pm10value").text();
					System.out.println("[디버그] 춘천(중앙로) 미세먼지 값 찾음: " + value);
				}
			}
		}
    	return value;
    }

	@Override
	public void connectionLost(Throwable arg0) {
		// TODO Auto-generated method stub
		System.out.println("Connection Lost");
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken arg0) {
		// TODO Auto-generated method stub		
		
	}

	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception {
		// TODO Auto-generated method stub
		if (topic.equals("led")) {
			System.out.println("--------------Actuator Function--------------");
			System.out.println("LED Display changed");
			System.out.println("LED: " + msg.toString());
			System.out.println("---------------------------------------------");
		}
		if (topic.equals("window/mode")) {
			currentMode = msg.toString().trim();
			System.out.println("[디버그] 모드 변경 수신: " + currentMode);
		}
	}    
}
