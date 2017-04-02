package Manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import Model.Pair;
import Model.SummaryMessage;


/* summarize the answers to one summary output file, upload it to s3 and send message to the 
 * application....(the url is the the pair second object)
 */
public class Sender implements Runnable {
	private BlockingQueue<Pair<HashMap<String, String>, String>> completeTaskQueue;
	private AmazonSQS sqsClient;
	private HashMap<String, Integer> taskSizeMap;
	private final String bucketName = "shahardavidovich92";

	public Sender(AmazonSQS sqsClient,HashMap<String,Integer> taskSizeMap) {
		this.sqsClient = sqsClient;
		this.taskSizeMap = taskSizeMap;
		this.completeTaskQueue = new LinkedBlockingQueue<Pair<HashMap<String, String>, String>>();
	}

	public void addToQueue(Pair<HashMap<String, String>, String> pair){
		try {
			completeTaskQueue.put(pair);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	//create 
	public File createSummaryFile(HashMap<String,String> map){
		File file=null;
		JSONObject o = new JSONObject();
		JSONArray[] arr = new JSONArray[map.size()];
		Iterator<?> it = map.entrySet().iterator();
		try {
			int i=0;
			while(it.hasNext()){
				Map.Entry<?,?> pair = (Entry<?, ?>)it.next();
				arr[i] = new JSONArray(pair.getValue().toString());
				i++;
			}
			JSONArray array = new JSONArray();
			int k=0;
			for(i = 0;i<map.size();i++){
				for(int j = 0;j<arr[i].length();j++){
					array.put(k,arr[i].get(j));
					k++;
				}
			}
			o.put("astroids",array);
			file = new File("summary"+UUID.randomUUID()+".json");
			file.createNewFile();
			PrintWriter writer = new PrintWriter(file, "UTF-8");
			writer.println(o.toString());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return file;
	}

	private SummaryMessage uploadToS3(File f) {
		PropertiesCredentials Credentials = null;
		try {
			Credentials = new PropertiesCredentials( new FileInputStream("prop.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		AmazonS3 S3 = new AmazonS3Client(Credentials);

		PutObjectRequest por = new PutObjectRequest(this.bucketName, f.getName(), f);

		// Upload the file
		System.out.println("sender uploaded a file.");

		S3.putObject(por);

		return new SummaryMessage(this.bucketName, f.getName());	
	}

	@Override
	public void run() {
		Pair<HashMap<String, String>, String> pair = null;
		System.out.println("sender started");
		try {
			pair = completeTaskQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("sender creating summary file...");
		File file = createSummaryFile(pair.getFirst());
		SummaryMessage summaryMessage = uploadToS3(file);
		sendSQSMessage(summaryMessage,pair.getSecond());
	}

	private void sendSQSMessage(SummaryMessage summaryMessage, String url) {
		System.out.println("sender sends sqs message to the application");
		sqsClient.sendMessage(new SendMessageRequest(url, summaryMessage.getMessageString()));
		taskSizeMap.remove(url);
	}

}