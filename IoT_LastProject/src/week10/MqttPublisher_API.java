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
import java.util.Date;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class MqttPublisher_API implements MqttCallback{
	static MqttClient sampleClient;
		
    public static void main(String[] args) {
    	MqttPublisher_API obj = new MqttPublisher_API();
    	obj.run();
    }
    
    public void run() {
		connectBroker();
    	try {
    		sampleClient.subscribe("led");
    	} catch (MqttException e) {
    		e.printStackTrace();
    	}
    	    	
    	while(true) {    		
        	try {
        		String pm_data = get_pm_data();
            	String[] weather_data  = get_weather_data();
            	 
            	publish_data("tmp", "{\"tmp\": "+weather_data[0]+"}");
            	publish_data("humi", "{\"humi\": "+weather_data[1]+"}");
            	publish_data("pm", "{\"pm\": "+pm_data+"}");
				Thread.sleep(5000);
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
        String broker       = "tcp://127.0.0.1:1883"; // Broker Server IP
        String clientId     = "practice"; // Client ID
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            sampleClient = new MqttClient(broker, clientId, persistence); // Initialization of MQTT Client
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: "+broker);
            sampleClient.connect(connOpts);
            sampleClient.setCallback(this); // Callback option �߰�
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
            System.out.println("Publishing message: "+data);
            sampleClient.publish(topic, data.getBytes(), qos, false);
            System.out.println("Message published");
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
    	Date current = new Date(System.currentTimeMillis());
    	SimpleDateFormat d_format = new SimpleDateFormat("yyyyMMddHHmmss"); 
    	//System.out.println(d_format.format(current));
    	String date = d_format.format(current).substring(0,8);    	
    	String time = d_format.format(current).substring(8,10);    	
    	String url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst" // https�� �ƴ� http ���������� ���� �����ؾ� ��.
    			+ "?serviceKey=1e181a891318026c035b8e44126be2bf2d1b9d8487c0a6cccae2a486fe6819d5"
    			+ "&pageNo=1&numOfRows=1000"
    			+ "&dataType=XML"
    			+ "&base_date="+ date //20230522, date
    			+ "&base_time=" + time + "00" //23, time+"00"
    			+ "&nx=55"
    			+ "&ny=127";
    	    	
		String temp = "";
		String humi = "";
				
    	Document doc = null;
		
		// 2.�Ľ�
		try {
			doc = Jsoup.connect(url).get();
		} catch (IOException e) {
			e.printStackTrace();
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
    
    //serviceKey ����
    public static String get_pm_data() {
    	String url = "http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/" // https�� �ƴ� http ���������� ���� �����ؾ� ��.
    			+ "getCtprvnRltmMesureDnsty"
    			+ "?serviceKey=1e181a891318026c035b8e44126be2bf2d1b9d8487c0a6cccae2a486fe6819d5"
    			+ "&returnType=xml"
    			+ "&numOfRows=100"
    			+ "&pageNo=1"
    			+ "&sidoName=%EA%B0%95%EC%9B%90"
    			+ "&ver=1.0"; //ũ�Ѹ��� url����
		String value = "";
    	Document doc = null;
		
		// 2.�Ľ�
		try {
			doc = Jsoup.connect(url).get();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(doc);
		
		Elements elements = doc.select("item");
		for (Element e : elements) {
			if (e.select("stationName").text().equals("춘천시")) {
				value = e.select("pm10Value").text();				
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
	}    
}
