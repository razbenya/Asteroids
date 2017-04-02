package Manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import Model.ApplicationFileMessage;
import Model.Message;

/* the job of the class manager is to listen to the sqs queue
 * of the Manager (this queue get's the requests from the applications).
 * as he get's a message , he push the big task to a queue in the reducer object,
 * and activate the reducer object using a thread pool - that way, many tasks will be splitted
 * in parallel. 
 * 
 * when the manager get's terminate message he no longer listen's to messages, and waiting for all
 * the in-proccess tasks to finish and than terminate (he know's how the in-proccess tasks finished
 * thank to the collecter).
 */
public class Manager {

	private static String inQueueURL;
	private static String outQueueURL;
	private static String completeQueueURL;
	private static boolean terminate;
	private static Reducer reducer;
	private static Thread collecterThread;
	private static Collecter collecter;

	private static AmazonSQSClient sqsClient;
	private static ThreadPoolExecutor executor;
	private final static String workerBucketName = "workerjavabucket";
	private final static String managerBucketName = "managerjavajarbucket";

	public static void main (String[] args )  {
		initialize(args[0]);
		while(!terminate){
			waitForNextTask();
		}
		////////////////////////////sleep on something shared with the collecter, the collecter will wake him up 
		//when all the tasks are done
		synchronized (collecter){
			while(!collecter.getTerminated()){
				try {
					System.out.println("manager waiting for all the tasks in process to finish...");
					collecter.wait();
					System.out.println("manager got notifide that all the tasks in process were finished");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}	
			}
		}
		System.out.println("manager terminating...");
		terminate();
	}



	private static void terminate() {
		ArrayList<String> instancesToTerminate = reducer.getWorkersIds();
		AmazonEC2 ec2 = null;
		try {
			ec2 = new AmazonEC2Client(new PropertiesCredentials(new FileInputStream(new File("prop.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
		}
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		List<String> valuesT1 = new ArrayList<String>();
		valuesT1.add("Manager");
		Filter filter1 = new Filter("tag:Manager", valuesT1);
		DescribeInstancesResult result = ec2.describeInstances(request.withFilters(filter1));
		List<Reservation> reservations = result.getReservations();
		for (Reservation reservation : reservations) {
			List<Instance> instances = reservation.getInstances();
			if(instances.size() > 0 && (!instances.get(0).getState().getName().equals("terminated") && !instances.get(0).getState().getName().equals("shutting-down")))
				instancesToTerminate.add(instances.get(0).getInstanceId());
		}
		try {

			TerminateInstancesRequest request2 = new TerminateInstancesRequest();
			request2.withInstanceIds(instancesToTerminate);
			System.out.println("manager terminating workers");
			ec2.terminateInstances(request2);
			
			AmazonS3 s3Client = new AmazonS3Client(new PropertiesCredentials(new FileInputStream("prop.properties"))); 
			s3Client.deleteObject(managerBucketName, "credentials.properties");
			s3Client.deleteObject(workerBucketName, "credentials.properties");
			
			System.out.println("manager deliting quqeues");
			//sleeping in order that the workers will terminate (without exception)
			Thread.sleep(1000);
			sqsClient.deleteQueue(new DeleteQueueRequest(inQueueURL));
			sqsClient.deleteQueue(new DeleteQueueRequest(outQueueURL));
			sqsClient.deleteQueue(new DeleteQueueRequest(completeQueueURL));
		} catch (AmazonServiceException ase) {
			System.out.println("Caught Exception: " + ase.getMessage());
			System.out.println("Reponse Status Code: " + ase.getStatusCode());
			System.out.println("Error Code: " + ase.getErrorCode());
			System.out.println("Request ID: " + ase.getRequestId());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}  catch (IOException e) {
			e.printStackTrace();
		}
		executor.shutdown();	
	}



	private static void waitForNextTask() {
		System.out.println("manager waiting for a message");
		ReceiveMessageRequest request = new ReceiveMessageRequest(inQueueURL);
		request.setWaitTimeSeconds(20);
		ReceiveMessageResult result;
		do{
			result = sqsClient.receiveMessage(request);
		} while(result.getMessages().isEmpty());

		for(com.amazonaws.services.sqs.model.Message mes : result.getMessages()){
			sqsClient.deleteMessage(new DeleteMessageRequest(inQueueURL,mes.getReceiptHandle()));
			Message curMessage = null;
			if(mes.getBody().startsWith("inputFile")){
				System.out.println("manager got a regular task message");
				curMessage = new ApplicationFileMessage();
				curMessage.stringToMessage(mes.getBody());
				reducer.addToQueue(curMessage);
				System.out.println("manager executing the task with the reducer");
				executor.execute(reducer);
			}
			if(mes.getBody().startsWith("terminate")){
				System.out.println("manager got a termination message");
				terminate = true;
				collecter.setShouldTerminate();
				break;
			}
		}			
	}

/*
 * getting everything to be ready to work:
 * sqs queue's
 * ececutor
 * collecter - also activat's him
 * reducer
 */
	private static void initialize(String password)  {
		System.out.println("manager started");
		executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
		terminate = false;
		dycryptCredentials(password);
		//get the manager queue
		try {
			sqsClient = new AmazonSQSClient(new PropertiesCredentials(new FileInputStream("prop.properties")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		inQueueURL = sqsClient.listQueues("M").getQueueUrls().get(0);
		//create the queqe for the workers, and for the answers
		CreateQueueRequest createQueueRequest = new CreateQueueRequest("outQueue"+ UUID.randomUUID());
		outQueueURL = sqsClient.createQueue(createQueueRequest).getQueueUrl();
		createQueueRequest = new CreateQueueRequest("completeQueue"+ UUID.randomUUID());
		completeQueueURL = sqsClient.createQueue(createQueueRequest).getQueueUrl();
		collecter = new Collecter(completeQueueURL,sqsClient);
		collecterThread = new Thread(collecter);
		reducer = new Reducer(outQueueURL,collecter,sqsClient, password);
		collecterThread.start();
	}


/*
 * decrypting the encypted credantials (located in s3)
 */
	public static void dycryptCredentials(String password) {

		try {
		//reading the file:
		Path path = Paths.get("credentials.properties");
		byte[] data = Files.readAllBytes(path);
			//decrypting
			SecretKeyFactory keyFactory2 = SecretKeyFactory.getInstance("DES");
			DESKeySpec keySpec2 = new DESKeySpec(password.getBytes("UTF-8"));
			SecretKey myAesKey2 = keyFactory2.generateSecret(keySpec2);

			Cipher desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
			desCipher.init(Cipher.DECRYPT_MODE, myAesKey2);
			byte[] textDecrypted = desCipher.doFinal(data);

			//create prop.properties

			FileOutputStream fos = new FileOutputStream("prop.properties");
			fos.write(textDecrypted);
			fos.close();
		}catch (Exception e) {
			e.printStackTrace();
		}


	}
}

