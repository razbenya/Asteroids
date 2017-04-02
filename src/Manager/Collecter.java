package Manager;

import java.util.HashMap;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import Model.CompleteTaskMessage;
import Model.Message;
import Model.Pair;


/*
 * the collecter always listens to the queue that get's complete tasks from the workers.
 * how the collecter know's when to activate the sender to summaries the output:
 * 
 * the reducer will have a counter , and every big message he gets he incresing the counter by one, and mark
 * all the small tasks by count - thats the way we know which tasks come together(same count). also the reducer
 * will inform the collecter to be ready to get x messages of count  y and that the summary of count y 
 * should be sent to a specific queueURL(he have this info) .so every
 * time the collecter gets complete message he will add it to:
 *  
 * HashMap<Integer,Pair<HashMap<String,String>,String>> (the inner hash map key
 * is: start-date and value is: the output corresponding to this start date in json format, the second 
 * element in the pair is (String) the sqs queue to return the answer about this message id.
 * 
 * and will check if the size of the inner HashMap arrived to the num of tasks with the Integer count
 * (stored in HashMap<String,Integer> taskSizeMap), if do,
 * the collecter activates the sender that will summarize the answers to one summary output file, upload it to s3 and send message to the 
 * application....(the url is tn the pair second object)
 * 
 * the collecter also have a special role in the termination of the manager:
 * if the manager got the teaminate message, he should teminate, but not before all the tasks 
 * in process will finish - and the collecter knows that information. so the manager will inform
 * the collecter that he should terminate. and the collecter will really teminate when he have no
 * more complete tasks to wait for(if taskSizeMap is empty it indicates that he have no more complete
 * tasks to wait for , and he will not get new one's because when the manager changes the "should terminate"
 * to true he no longer listens to tasks.) just before the collecter will terminate he wake's the 
 * manager up to terminate.
 */

public class Collecter implements Runnable {
	private String completeQueueURL;

	private boolean shouldTerminate;

	//mapping the size of each task (number of sub tasks) 
	private HashMap<String,Integer> taskSizeMap;

	//key = count of task from the reducer , value = pair of list of result(jsonObject) and return queue url address
	private HashMap<Integer,Pair<HashMap<String,String>,String>> taskInfo;

	private Boolean terminated;
	private AmazonSQS sqsClient;
	private Sender sender;
	private ThreadPoolExecutor executor;

	public Collecter(String completeQueueURL,AmazonSQS sqsClient){
		this.sqsClient = sqsClient;
		this.completeQueueURL = completeQueueURL;
		this.taskSizeMap = new HashMap<String,Integer>();
		taskInfo = new HashMap<Integer,Pair<HashMap<String,String>,String>>();
		this.shouldTerminate = false;
		terminated = false;
		sender = new Sender(sqsClient,taskSizeMap);
		executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
	}

	public Boolean getShouldTerminate() {
		return shouldTerminate;
	}

	public void setShouldTerminate() {
		System.out.println("collecter recivied should terminate");
		this.shouldTerminate = true;
	}

	public void informCollecter(int id,int amount,String url){
		Pair<HashMap<String,String>,String> pair = new Pair<HashMap<String,String>,String>(new HashMap<String,String>(),url);
		taskSizeMap.put(url,amount);
		this.taskInfo.put(id,pair);
		System.out.println("collecter was infomed about " + amount + " messages of id:" + id);
	}

	private void addTask(CompleteTaskMessage m){
		Pair<HashMap<String,String>,String> pair = taskInfo.get(m.getId());
		System.out.println("collecter recived a complete message starts with date: " +m.getStartDate() + " with id: " + m.getId());
		pair.getFirst().put(m.getStartDate(),m.getJsonString());
		if(taskSizeMap.get(pair.getSecond()) == pair.getFirst().size()){
			int key = m.getId();
			sender.addToQueue(this.taskInfo.get(key));
			this.taskInfo.remove(key);
			this.executor.execute(sender);
		}
	}


	private void waitForNextTask() {
		System.out.println("collecter waiting for a message");
		ReceiveMessageRequest request = new ReceiveMessageRequest(completeQueueURL);
		request.setWaitTimeSeconds(20);
		ReceiveMessageResult result = null;
		do{
			if(shouldTerminate && taskSizeMap.isEmpty())
				return;
			result = sqsClient.receiveMessage(request);
		} while(result.getMessages().isEmpty());
		System.out.println("collecter got complete Tasks");

		for(com.amazonaws.services.sqs.model.Message mes : result.getMessages()){
			sqsClient.deleteMessage(new DeleteMessageRequest(completeQueueURL,mes.getReceiptHandle()));
			Message curMessage = null;
			if(mes.getBody().startsWith("complete")){
				curMessage = new CompleteTaskMessage();
				curMessage.stringToMessage(mes.getBody());
				CompleteTaskMessage message = (CompleteTaskMessage) curMessage;
				addTask(message);
			}

		}			
	}


	@Override
	public void run() {
		System.out.println("collecter started");
		while(!terminated){
			waitForNextTask();
			terminated = this.shouldTerminate&&this.taskSizeMap.isEmpty();
		}
		executor.shutdown();
		synchronized (this){
			notifyAll();
		}
	}

	public Boolean getTerminated() {
		return this.terminated;
	}
}