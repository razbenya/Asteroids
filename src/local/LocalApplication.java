package local;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;
import Model.*;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class LocalApplication {

	private static String queueURL;
	private static String inputFileLocation;
	private static String outputFileLocation;
	private static String n;
	private static String d;
	private static AmazonSQS sqs;
	private static final String managerBucketName = "managerjavajarbucket";
	private static final String workerBucketName = "workerjavabucket";
	
	public static void main (String[] args ) throws IOException {

		initialize(args);

		//Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node.

		checkManager();


		//Uploads the file to S3.
		ApplicationFileMessage result = uploadToS3(inputFileLocation, false);
		System.out.println("input file uploaded.");
		
		//Sends a message to an SQS queue, stating the location of the file on S3
		sendSQSMessage(result);

		//Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.

		//Downloads the summary file from S3, and create an html file representing the results.

		waitForMessage();

		//Sends a termination message to the Manager if it was supplied as one of its input arguments.
		if(args.length == 5 && args[4].equals("terminate")){
			terminateManager();
		}
		terminate();
	}

	/*
	 * I: hash map of astroids
	 * O: an html file with the correct color
	 */

	public static void returnHTMLfile(HashMap<Long,Astroid> astroids) throws IOException {
		System.out.println("creating HTML file");
		File f = new File(outputFileLocation);
		//bgcolor='#ebebe0'
		//sorting the map
		List<Astroid> values = new ArrayList<Astroid>(astroids.values());
		Collections.sort(values);
		Iterator<Astroid> it = values.iterator();
		BufferedWriter bw = new BufferedWriter(new FileWriter(f));
		StringBuffer html = new StringBuffer();
		html.append("<html> <head> <style> table, th, td {  border: 1px solid black; border-collapse: collapse;	 th, td {padding: 5px;text-align: left; }");
		html.append("</style> </head> <body background='https://s3.amazonaws.com/shahardavidovich92/background.jpg' > <h2><center>Asteroids output file</center></h2><table style='width:100%'>");
		html.append("<tr bgcolor='#ebebe0'>");
		html.append("<th>#</th>");
		html.append("<th>Name</th>");
		html.append("<th> Close Approach Date</th>");
		html.append("<th> Relative Velocity(kmps)</th>");
		html.append("<th> Min Estimated Diameter(m)</th>");
		html.append("<th> Max Estimated Diameter(m)</th>");
		html.append("<th> Miss Distance (k) </th>");
		html.append(" </tr>");
		int i=0;
		while (it.hasNext()) 
			html.append(it.next().toHtml(i++));
		html.append("</table></body></html>");
		bw.write(html.toString());
		bw.close();
	}

	/*
	 * waiting for a summary message from the manager
	 */
	private static void waitForMessage() throws FileNotFoundException, IOException {
		System.out.println("waiting for a complete message");
		AmazonSQS sqs = new AmazonSQSClient(new PropertiesCredentials(new FileInputStream("prop.properties")));
		ReceiveMessageRequest request = new ReceiveMessageRequest(queueURL);
		request.setWaitTimeSeconds(20);
		ReceiveMessageResult result;
		do{
			result = sqs.receiveMessage(request);
		}while (result.getMessages().isEmpty());
		System.out.println("application got summary message");
		String msg = result.getMessages().get(0).getBody();
		if(msg.startsWith("summary")){
			SummaryMessage s = new SummaryMessage();
			s.stringToMessage(msg);	
			String jsonFile = readfile(s.getBucketName(),s.getFileName());
			HashMap<Long,Astroid> astroids = answerFileToHashMap(jsonFile);
			returnHTMLfile(astroids);
		}
	}

	/*
	 * reading the summary file
	 */
	private static String readfile(String bucketName, String fileName) throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
		System.out.println("get's the summary file from s3");
		AmazonS3 s3Client = new AmazonS3Client(new PropertiesCredentials(new FileInputStream("prop.properties")));        
		S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, fileName));
		InputStream objectData = object.getObjectContent();
		s3Client.deleteObject(bucketName, fileName);
		BufferedReader reader = new BufferedReader(new InputStreamReader(objectData));
		StringBuilder ans = new StringBuilder();
		while (true) {
			String line = reader.readLine();
			if (line == null) break;
			ans.append(line);
		}
		objectData.close();
		return ans.toString();

	}

	/*
	 *  parse json and return hashmap of astroids
	 */
	private static HashMap<Long,Astroid> answerFileToHashMap(String json) {
		HashMap<Long,Astroid> astroids = new HashMap<Long,Astroid>();
		//String outPutStreamReader = new outPutStreamReader(new FileReader("c:\\test.json"));
		JSONObject o;
		try {
			o = new JSONObject(json);
			JSONArray array = o.getJSONArray("astroids");
			for(int i=0;i<array.length();i++){
				Astroid astroid = new Astroid(
						array.getJSONObject(i).getLong("id"),
						array.getJSONObject(i).getString("name"),
						array.getJSONObject(i).getDouble("speed"),
						array.getJSONObject(i).getDouble("diameter_max"),
						array.getJSONObject(i).getDouble("diameter_min"),
						array.getJSONObject(i).getDouble("miss_distanceA"),
						array.getJSONObject(i).getDouble("miss_distanceK"),
						array.getJSONObject(i).getString("date"),true
						);
				astroid.setColor(array.getJSONObject(i).getString("color"));
				astroids.put(astroid.getId(), astroid);
			}

		} catch (JSONException e) {
			e.printStackTrace();
		}
		return astroids;	
	}

	/*
	 * delete the application queue
	 */
	private static void terminate() throws FileNotFoundException, IOException {

		AmazonSQS sqs = new AmazonSQSClient(new PropertiesCredentials(new FileInputStream("prop.properties")));

		System.out.println("Deleting the application queue");
		sqs.deleteQueue(new DeleteQueueRequest(queueURL));
		System.out.println("bye bye");

	}

	/*
	 * initialize all the data and create an application queue
	 */
	private static void initialize(String[] args) throws FileNotFoundException, IOException {
		inputFileLocation = args[0];
		outputFileLocation = args[1];
		n = args[2];
		d = args[3];
		if(Integer.parseInt(d) > 7) d = "7";
		sqs = new AmazonSQSClient(new PropertiesCredentials(new FileInputStream("prop.properties")));
		CreateQueueRequest createQueueRequest = new CreateQueueRequest("ApplicationQueue"+ UUID.randomUUID());
		queueURL = sqs.createQueue(createQueueRequest).getQueueUrl();


	}

	/*
	 * send a Message to the Manager sqs specify the location of the input file on s3
	 */
	private static void sendSQSMessage(ApplicationFileMessage message) throws FileNotFoundException, IOException {
		// Send a message
		try {
			//waiting till manager queue is available (probably AWS problem)
			System.out.print("waiting for manager queue..");
			while(sqs.listQueues("M").getQueueUrls().isEmpty()){
				System.out.print(".");
				Thread.sleep(1000);
			}
			System.out.println();
			String ManagaerQueueURL = sqs.listQueues("M").getQueueUrls().get(0);
			System.out.println("Sending a message to manager");
			sqs.sendMessage(new SendMessageRequest(ManagaerQueueURL, message.getMessageString()));

		} catch (AmazonServiceException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/*
	 * upload the input file to s3
	 */
	private static ApplicationFileMessage uploadToS3(String fileLocation, Boolean isPublic) throws FileNotFoundException, IOException {
		PropertiesCredentials Credentials = new PropertiesCredentials( new FileInputStream("prop.properties"));
		AmazonS3 S3 = new AmazonS3Client(Credentials);
		File f = new File(fileLocation);
		PutObjectRequest por = null;
		PutObjectRequest por2 = null;
		// Upload the file
		
		if(isPublic){
			por = new PutObjectRequest(managerBucketName, f.getName(), f)
					.withCannedAcl(CannedAccessControlList.PublicRead);
			por2 = new PutObjectRequest(workerBucketName, f.getName(), f)
					.withCannedAcl(CannedAccessControlList.PublicRead);
		}
		else
			por =  new PutObjectRequest(managerBucketName, f.getName(), f).withCannedAcl(CannedAccessControlList.PublicRead);
		S3.putObject(por);
		if(isPublic)
			S3.putObject(por2);
		return new ApplicationFileMessage(managerBucketName, f.getName(),queueURL,n,d);
	}

	/*
	 * send a message to the manager that its is last job
	 */
	private static void terminateManager() throws FileNotFoundException, IOException {
		//send terminate mesage to the manager queue!!!
		String ManagaerQueueURL = sqs.listQueues("Manager").getQueueUrls().get(0);
		System.out.println("Sending termination message to manager");
		Message message = new ManagerTerminateMessage(queueURL);
		sqs.sendMessage(new SendMessageRequest(ManagaerQueueURL, message.getMessageString()));
	}

	public static String managerScript(String key)
	{
		ArrayList<String> lines = new ArrayList<String>();
		lines.add("#!/bin/bash");
		lines.add("wget " + "https://s3.amazonaws.com/"+managerBucketName+"/credentials.properties");
		lines.add("wget " + "https://s3.amazonaws.com/"+managerBucketName+"/Manager.jar");
		lines.add("echo y|sudo yum install java-1.8.0");
		lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
		lines.add("java -jar Manager.jar "+key);
		String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
		return str;
	}
	static String join(Collection<String> s, String delimiter) {
		StringBuilder builder = new StringBuilder();
		Iterator<String> iter = s.iterator();
		while (iter.hasNext()) {
			builder.append(iter.next());
			if (!iter.hasNext()) {
				break;
			}
			builder.append(delimiter);
		}
		return builder.toString();
	}

	/*
	 * check if the manager is active if not run a manager node.
	 */
	private static void checkManager() throws IOException {

		boolean exist = false;
		AmazonEC2 ec2;
		AWSCredentials credentials = new PropertiesCredentials( new FileInputStream(new File("prop.properties")));
		ec2 = new AmazonEC2Client(credentials);
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		List<String> valuesT1 = new ArrayList<String>();
		valuesT1.add("Manager");
		Filter filter1 = new Filter("tag:Manager", valuesT1);
		DescribeInstancesResult result = ec2.describeInstances(request.withFilters(filter1));
		List<Reservation> reservations = result.getReservations();
		for (Reservation reservation : reservations) {
			List<Instance> instances = reservation.getInstances();
			if(instances.size() > 0 && (!instances.get(0).getState().getName().equals("terminated") && !instances.get(0).getState().getName().equals("shutting-down")))
				exist = true;
		}
		if(!exist){
			if(sqs.listQueues("M").getQueueUrls().isEmpty()) {
				CreateQueueRequest createQueueRequest = new CreateQueueRequest("Manager"+UUID.randomUUID());
				sqs.createQueue(createQueueRequest);
			}
			//upload encrypted credentials to s3
			String key = uploadEncryptedCredentials();
			System.out.println("Encrypted Credentials File uploaded.");
			//creating a new manager
			RunInstancesRequest Runrequest = new RunInstancesRequest("ami-b73b63a0", 1, 1).withKeyName("shahard92");
			Runrequest.setInstanceType(InstanceType.T2Micro.toString());
			Runrequest.setUserData(managerScript(key));
			List<Instance> instances = ec2.runInstances(Runrequest).getReservation().getInstances();
			//tagging the manager as <Manager, Manager> as <key, value>
			List<String> idList = new ArrayList<String>();
			idList.add(instances.get(0).getInstanceId());

			Collection<Tag> tagCollection = new ArrayList<Tag>();
			tagCollection.add(new Tag("Manager","Manager"));
			CreateTagsRequest tagRequest = new CreateTagsRequest(idList,(List<Tag>) tagCollection);
			ec2.createTags(tagRequest);
			System.out.println("Starting Manager");
		}
		else {
			System.out.println("there is already a Manager ready to work");

		}
	}

	private static String uploadEncryptedCredentials() {
		//getting the data from the file
		String uuid = null;
		try {
			Path path = Paths.get("prop.properties");
			byte[] data = Files.readAllBytes(path);
			//encrypting
			uuid = UUID.randomUUID().toString();
			DESKeySpec keySpec = new DESKeySpec(uuid.getBytes("UTF-8"));
			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
			SecretKey myAesKey = keyFactory.generateSecret(keySpec);
			Cipher desCipher;
			desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
			desCipher.init(Cipher.ENCRYPT_MODE, myAesKey);
			byte[] textEncrypted = desCipher.doFinal(data);
			//creates credentials file
			FileOutputStream fos = new FileOutputStream("credentials.properties");
			fos.write(textEncrypted);
			fos.close();
			//upload the credentials file to s3
			uploadToS3("credentials.properties",true);
			File f = new File("credentials.properties");
			f.delete();

		} catch(Exception e) {
			System.out.println("problem encrypting");
		} 
		return uuid;
	}
}
