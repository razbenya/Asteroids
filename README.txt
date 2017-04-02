####################### ReadMe File #####################################

Name: Raz Ben-Yaish 
Name: Shahar Davidovich	
######################## running instructions ###########################

the credentials file(of Shahar) should be in the same folider as the main program and named: prop.properties
Manager and Worker jars should be in the s3 as following: 
Manager.jar in the managerjavajarbucket,
Worker.jar in the workerjavabucket.
input file should be a json file accourding to "inputFileExample.json" format.

######################## explanation #####################################

Application: 

1. initializing : create an sqs queue for the return message. 
2. encrypt the credentials file and upload it to s3.
3. check if there is a runing manager, if not start it and open an sqs queue for him.
4. upload the input file to s3.
5. send message to the manager using sqs containg: location of the s3 file , n parameter , d parameter , url of the returning queue.
6. wait for a message back. 
7. create an output html file.
8. if requested sends terminate message to the manager.
9. deleting his queue.

Manager: 

the manager job divided into 4 class.
1. Manager - 
 * the job of the class manager is to listen to the sqs queue
 * of the Manager (this queue get's the requests from the applications).
 * as he get's a message , he push the big task to a queue in the reducer object,
 * and activate the reducer object using a thread pool - that way, many tasks will be splitted
 * in parallel. 
 * 
 * when the manager get's terminate message he no longer listen's to messages, and waiting for all
 * the in-proccess tasks to finish and than terminate (he know's how the in-proccess tasks finished
 * thank to the collecter).

2. Reducer - 
 * the reducer job is: given the big tasks from the manager, in each run of the reducer,
 * he takes one big task from the queue and split it into small tasks according to 'd'
 * parameter and activates the amount of workers needed according to n and the amount
 * of workers that already working. the reducer sends the small tasks to the workers
 * queue.
 * 
 * the reducer also informs the collecter abount a banch of missions that were send - 
 * let the collecter know how many complete-tasks he should get from this banch id.

3. Collecter - 
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

4. Sender - 
 * summarize the answers to one summary output file, upload it to s3 and send message to the 
 * application....(the url is the the pair second object)

Worker: 

 * the worker job is to listen to the shared sqs queue of all the workers. each time
 * he get's a task from this queue, the task indicates the start date and end date that
 * the worker need to check about the astroids, so the worker get's the nasa's answer about 
 * this dates (using api request), he orgenize's the json answer of nasa into hashmap of astorids
 * objects, and than organazing the output needed about these dates using json format, and send it 
 * to the sqs queue of the manager(the collecter will get this message).


####################################### type of instance ########################################

ami-b73b63a0 T2 Micro.

##################################### input and parameters ####################################

start-date = 30/10/2016
end-date = 27/01/2017
speed-threshold = 10 km/s
diameter-threshold = 200 m
miss-threshold = 0.3 AU
n = 11
d = 2

Num of Workers: 4 (see statistics for more details)
Running time: 2:32:07


##################################################################################################
