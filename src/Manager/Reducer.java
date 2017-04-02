package Manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import Model.ApplicationFileMessage;
import Model.Message;
import Model.MyDate;
import Model.NewTaskMessage;
import Model.TaskDetails;


/* the reducer job is: given the big tasks from the manager, in each run of the reducer,
 * he takes one big task from the queue and split it into small tasks according to 'd'
 * parameter and activates the amount of workers needed according to n and the amount
 * of workers that already working. the reducer sends the small tasks to the workers
 * queue.
 * 
 * the reducer also informs the collecter abount a banch of missions that were send - 
 * let the collecter know how many complete-tasks he should get from this banch id
 */
public class Reducer implements Runnable{

	
	private String outQueueURL;
	private Integer numOfWorkers;
	private BlockingQueue<ApplicationFileMessage> messageQueue;
	private int taskCounter;
	private Collecter collecter;
	//first Integer is n of the task, and second is how many new workers we activate with this main task
	private ArrayList<String> listOfWorkers;
	private static AmazonSQSClient sqsClient;
	private String password;
	private final String workerBucketName = "workerjavabucket";

	public Reducer (String outQueueURL,Collecter collecter,AmazonSQSClient sqsClient1, String password) {
		this.collecter = collecter;
		this.password = password;
		this.outQueueURL = outQueueURL;
		sqsClient = sqsClient1;
		numOfWorkers = 0;
		taskCounter = 0;
		//tasksEnded = 0;
		messageQueue = new LinkedBlockingQueue<ApplicationFileMessage>();
		listOfWorkers = new ArrayList<String>();
	}

	public ArrayList<String> getWorkersIds () {
		return listOfWorkers;
	}

	public void addToQueue(Message message){
		try {
			messageQueue.put((ApplicationFileMessage) message);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		System.out.println("reducer started");
		String toParse;
		ApplicationFileMessage message=null;
		try {
			message = messageQueue.take();
			System.out.println("reducer took a Mainmessage");

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		

		toParse = readfile(message.getBucketName(), message.getFileName());
		TaskDetails mainTask = getFields(toParse);
		//splits the main task into small tasks according to d
		System.out.println("reducer splitting the tasks");
		ArrayList<TaskDetails> tasks = splitToTasks(mainTask,Integer.parseInt(message.getD()));
		int newTasks = tasks.size();
		// inform the collecter
		synchronized (this){
			if(message != null)
				taskCounter++;
		collecter.informCollecter(taskCounter,newTasks,message.getUrl());
		System.out.println("reducer adding "+ newTasks + " reduced tasks, with id " + taskCounter + " to the workers queue");
		addTaskToOutSQS(tasks);
		}
		
	
		int n = Integer.parseInt(message.getN());
		int m = (int) Math.ceil(newTasks/n);
		int workersToAdd = m - numOfWorkers;
		if(this.numOfWorkers + workersToAdd > 19)
			workersToAdd = 19-numOfWorkers;
		numOfWorkers += workersToAdd;
		System.out.println("reducer adding " + workersToAdd + " workers");
		if(workersToAdd > 0) {
			addWorkers(workersToAdd);
			
		}
	}

	public Integer getNumOfWorkers() {
		return numOfWorkers;
	}
	public void setNumOfWorkers(int numOfWorkers) {
		this.numOfWorkers = numOfWorkers;
	}
	private void addWorkers(int workersToAdd) {
		AmazonEC2 ec2 = null;
		try {
			ec2 = new AmazonEC2Client(new PropertiesCredentials(new FileInputStream(new File("prop.properties"))));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		List<String> idList = new ArrayList<String>();
		RunInstancesRequest Runrequest = new RunInstancesRequest();
		Runrequest.withImageId("ami-b73b63a0")
		.withInstanceType("t2.micro")
		.withMinCount(workersToAdd)
		.withMaxCount(workersToAdd)
		.withKeyName("shahard92")
		.withSecurityGroups("default");
		Runrequest.setUserData(getUserDataScript());
		List<Instance> instances = ec2.runInstances(Runrequest).getReservation().getInstances();
		for (Instance instance: instances){
			idList.add(instance.getInstanceId());
			listOfWorkers.add(instance.getInstanceId());
		}
		//tagging the workers as <Worker, Worker> as <key, value>
		Collection<Tag> tagCollection = new ArrayList<Tag>();
		tagCollection.add(new Tag("Worker","Worker"));
		CreateTagsRequest tagRequest = new CreateTagsRequest(idList,(List<Tag>) tagCollection);
		ec2.createTags(tagRequest);
	}

	private String getUserDataScript() {
		ArrayList<String> lines = new ArrayList<String>();
		lines.add("#!/bin/bash");
		lines.add("wget https://s3.amazonaws.com/"+workerBucketName+"/credentials.properties");
		lines.add("wget https://s3.amazonaws.com/"+workerBucketName+"/Worker.jar");
		lines.add("echo y|sudo yum install java-1.8.0");
        lines.add("echo y|sudo yum remove java-1.7.0-openjdk");
		lines.add("java -jar Worker.jar "+ password);
		String str = new String(Base64.encodeBase64(join(lines, "\n").getBytes()));
		return str;
	}

	private String join(Collection<String> s, String delimiter) {
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

	private ArrayList<TaskDetails> splitToTasks(TaskDetails mainTask, int d) {
		ArrayList<TaskDetails> ans = new ArrayList<TaskDetails>();
		MyDate currentDate = mainTask.getStart_date();
		MyDate nextDate = currentDate.addDays(d-1);
		while(nextDate.compareTo(mainTask.getEnd_date()) <= 0){
			TaskDetails currentTask = new TaskDetails(currentDate,nextDate, mainTask.getSpeed_threshold(), 
					mainTask.getDiameter_threshold(),  mainTask.getMiss_threshold() );
			ans.add(currentTask);
			currentDate = nextDate.addDays(1);
			nextDate = currentDate.addDays(d-1);	
		}
		if(currentDate.compareTo(mainTask.getEnd_date()) < 0){
			TaskDetails currentTask = new TaskDetails(currentDate,mainTask.getEnd_date(), mainTask.getSpeed_threshold(), 
					mainTask.getDiameter_threshold(),  mainTask.getMiss_threshold() );
			ans.add(currentTask);
		}
		

		return ans;
	}
	
	private void addTaskToOutSQS(ArrayList<TaskDetails> currentTasks) {		
		for(TaskDetails task : currentTasks) {
			SendMessageRequest req = new SendMessageRequest(outQueueURL, createMessage(task));
			Map<String,MessageAttributeValue> messageAttributes = new HashMap<String,MessageAttributeValue>();
			messageAttributes.put("speed", new MessageAttributeValue().withStringValue(task.getSpeed_threshold()+"").withDataType("String"));
			messageAttributes.put("diameter", new MessageAttributeValue().withStringValue(task.getDiameter_threshold()+"").withDataType("String"));
			messageAttributes.put("miss", new MessageAttributeValue().withStringValue(task.getMiss_threshold()+"").withDataType("String"));
			messageAttributes.put("start_date", new MessageAttributeValue().withStringValue(task.getStart_date()+"").withDataType("String"));
			req.setMessageAttributes(messageAttributes);
			sqsClient.sendMessage(req);
		}
	}
	

	private String createMessage(TaskDetails task){
		String url = "https://api.nasa.gov/neo/rest/v1/feed?start_date="+task.getStart_date().toString()+"&end_date="+task.getEnd_date().toString()+"&speed_threshold="+task.getSpeed_threshold()+"&diameter_threshold="+task.getDiameter_threshold()+"&miss_threshold="+task.getMiss_threshold()+"&api_key=HalWFZj1aNJCJTRNgyLmiivnZQ6ffVMSZX70li29";
		NewTaskMessage message = new NewTaskMessage(taskCounter,url);
		return message.getMessageString();
	}
	private TaskDetails getFields(String toParse) {
		TaskDetails task = null;
		try {
			JSONObject o = new JSONObject(toParse);
			String[] tmp1 = o.getString("start-date").split("/");
			String[] tmp2 = o.getString("end-date").split("/");
			task = new TaskDetails(
					new MyDate(Integer.parseInt(tmp1[2]),Integer.parseInt(tmp1[1]),Integer.parseInt(tmp1[0])),
					new MyDate(Integer.parseInt(tmp2[2]),Integer.parseInt(tmp2[1]),Integer.parseInt(tmp2[0])),
					o.getDouble("speed-threshold"),
					o.getDouble("diameter-threshold"),
					o.getDouble("miss-threshold")
					);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return task;
	}
	private String readfile(String bucketName, String fileName){
		AmazonS3 s3Client = null;
		StringBuilder ans = null;
		try {
			s3Client = new AmazonS3Client(new PropertiesCredentials(new FileInputStream("prop.properties")));

			S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, fileName));
			InputStream objectData = object.getObjectContent();
			s3Client.deleteObject(bucketName, fileName);
			BufferedReader reader = new BufferedReader(new InputStreamReader(objectData));
			ans = new StringBuilder();
			while (true) {
				String line = null;

				line = reader.readLine();

				if (line == null) break;
				ans.append(line);
			}

			objectData.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ans.toString();

	}

}
