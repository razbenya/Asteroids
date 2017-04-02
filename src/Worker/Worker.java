package Worker;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import Model.Astroid;
import Model.CompleteTaskMessage;
import Model.NewTaskMessage;


/*
 * the worker job is to listen to the shared sqs queue of all the workers. each time
 * he get's a task from this queue, the task indicates the start date and end date that
 * the worker need to check about the astroids, so the worker get's the nasa's answer about 
 * this dates (using api request), he orgenize's the json answer of nasa into hashmap of astorids
 * objects, and than organazing the output needed about these dates using json format, and send it 
 * to the sqs queue of the manager(the collecter will get this message).
 */
@SuppressWarnings("deprecation")
public class Worker {

	private static String inQueueURL;
	private static String completeQueueURL;
	private static NewTaskMessage currentMessage;
	private static ReceiveMessageResult currentResult;
	private static AmazonSQSClient sqsClient;
	private static int totalCounter;
	private static int dangerCounter;
	private static int safeCounter;


	public static void main(String[] args){
		initialize(args[0]);
		while(true){
			getNextMessage();
			String toParse = getJsonString(currentMessage.getUrl());
			HashMap<Long,Astroid> astroids = parse(toParse);
			CompleteTaskMessage toSend = buildMessage(astroids,currentMessage.getID());
			sendToManager(toSend);
			try{
				sqsClient.deleteMessage(new DeleteMessageRequest(inQueueURL,currentResult.getMessages().get(0).getReceiptHandle()));
			} catch(Exception e) {
				System.out.println("probably 2 workers took the same task");
			}
			//printing for the statistics of the worker, we will be interested about the last print
			System.out.println("this worker parsed " + totalCounter + " astroids, " + dangerCounter + " were "
					+ "dangerouse, and "+ safeCounter+ " were safe");
		}
	}
	private static void initialize(String password){
		Manager.Manager.dycryptCredentials(password);
		System.out.println("new worker started");
		currentMessage = null;
		totalCounter = 0;
		dangerCounter = 0;
		safeCounter = 0;
		try {
			sqsClient = new AmazonSQSClient(new PropertiesCredentials(new FileInputStream("prop.properties")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		inQueueURL = sqsClient.listQueues("outQueue").getQueueUrls().get(0);
		completeQueueURL = sqsClient.listQueues("completeQueue").getQueueUrls().get(0);

	}



	private static void sendToManager(CompleteTaskMessage toSend) {
		sqsClient.sendMessage(new SendMessageRequest(completeQueueURL, toSend.getMessageString()));
	}

	private static CompleteTaskMessage buildMessage(HashMap<Long, Astroid> astroids, int id) {
		Map<String,MessageAttributeValue> messageAttributes = currentResult.getMessages().get(0).getMessageAttributes();
		Iterator<Entry<Long, Astroid>> iter = astroids.entrySet().iterator();
		JSONArray arr = new JSONArray();
		while(iter.hasNext()){
			Astroid astroid = iter.next().getValue();
			astroid.setColor(Double.parseDouble(messageAttributes.get("speed").getStringValue()), 
					Double.parseDouble(messageAttributes.get("diameter").getStringValue()), 
					Double.parseDouble(messageAttributes.get("miss").getStringValue()));
			if(!astroid.getColor().equals(""))
				dangerCounter++;
			JSONObject o = new JSONObject();

			try {
				o.put("id", astroid.getId());
				o.put("name", astroid.getName());
				o.put("speed", astroid.getSpeed());
				o.put("diameter_max",astroid.getDiameter_max());
				o.put("diameter_min", astroid.getDiameter_min());
				o.put("hazardous", astroid.isHazardos());
				o.put("miss_distanceA", astroid.getMissDistanceA());
				o.put("miss_distanceK", astroid.getMissDistanceK());
				o.put("date",astroid.getClose_approach_date());
				o.put("color", astroid.getColor());
			} catch (JSONException e) {
				e.printStackTrace();
			}
			arr.put(o);
		}
		return new CompleteTaskMessage(id,arr.toString(), messageAttributes.get("start_date").getStringValue());
	}

	public static HashMap<Long, Astroid> parse(String toParse){
		JSONObject json;
		int numOfEle = 0;
		JSONObject neo = null;
		HashMap<Long,Astroid> Astroids = new HashMap<Long,Astroid>();
		try {
			json = new JSONObject(toParse);
			neo = json.getJSONObject("near_earth_objects");
			numOfEle = json.getInt("element_count");

			JSONObject[] astroids = new JSONObject[numOfEle];
			JSONArray[] dates = new JSONArray[neo.length()];
			Iterator<?> jsonIter = neo.keys();
			int i=0,k=0;
			while(jsonIter.hasNext()){

				dates [i] = neo.getJSONArray(jsonIter.next().toString());

				for(int j=0;j<dates[i].length();j++){
					astroids[k] = dates[i].getJSONObject(j);
					k++;
				}
				i++;
			}

			totalCounter += numOfEle;
			for(int j=0;j<numOfEle;j++){
				Astroid astroid = null;
				if(astroids[j].getBoolean("is_potentially_hazardous_asteroid")){
					astroid = new Astroid(astroids[j].getLong("neo_reference_id"),astroids[j].getString("name"),
							astroids[j].getJSONArray("close_approach_data").getJSONObject(0).getJSONObject("relative_velocity").getDouble("kilometers_per_second"),
							astroids[j].getJSONObject("estimated_diameter").getJSONObject("meters").getDouble("estimated_diameter_max"),
							astroids[j].getJSONObject("estimated_diameter").getJSONObject("meters").getDouble("estimated_diameter_min"),
							astroids[j].getJSONArray("close_approach_data").getJSONObject(0).getJSONObject("miss_distance").getDouble("astronomical"),
							astroids[j].getJSONArray("close_approach_data").getJSONObject(0).getJSONObject("miss_distance").getDouble("kilometers"),
							astroids[j].getJSONArray("close_approach_data").getJSONObject(0).getString("close_approach_date"),
							true);

					Astroids.put(astroid.getId(),astroid);
				}
				else {
					safeCounter++;
				}
			}
		}catch (JSONException e) {
			e.printStackTrace();
		}

		return Astroids;
	}

	public static String getJsonString(String urlStr) {
		@SuppressWarnings("resource")
		HttpClient httpclient = new DefaultHttpClient();

		HttpGet httpget = new HttpGet(urlStr);

		HttpResponse response = null;
		StringBuffer buffer = null;
		try {
			response = httpclient.execute(httpget);
			buffer = new StringBuffer();
			char[] dataLength = new char[1024];
			int read;
			BufferedReader rd = null;
			rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			while ((read = rd.read(dataLength)) != -1) {
				buffer.append(dataLength,0,read);
			}

		}catch (UnsupportedOperationException | IOException e1) {
			e1.printStackTrace();
		}
		return buffer.toString();
	}


	private static void getNextMessage() {
		ArrayList<String> list = new ArrayList<String>();
		list.add("speed");list.add("diameter");list.add("miss");list.add("start_date");
		ReceiveMessageRequest request = new ReceiveMessageRequest(inQueueURL).withMessageAttributeNames(list);
		request.setMaxNumberOfMessages(1);
		request.setWaitTimeSeconds(20);
		request.setVisibilityTimeout(20);
		do{
			currentResult = sqsClient.receiveMessage(request);
		}while (currentResult.getMessages().isEmpty());
		System.out.println("worker got a message");
		String msg = currentResult.getMessages().get(0).getBody();
		if(msg.startsWith("task")){
			currentMessage = new NewTaskMessage();
			currentMessage.stringToMessage(msg);	
		}
	}



}