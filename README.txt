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


################################ Mandatory Requirements ########################################

security: 
the sensetive part in this project is the credentials file becouse if someone will get his hand on it he can activate 
computers in the ec2 on our account, wich can get to money loss.
the only place that the credentials information is stored openly(except of the local application computer) is in s3 
but if a hacker will get there he will see "gibrish" : we encrypted the credentials file with Cipher java class using 
DES ( Data Encryption Standard) Alogrithem. 

persistence: 
we thought what happens if each of the nodes dies and we came to a conclusion that if the manager dies there is nothing that
we can realy do (but the next running application will create a new manager and all the old tasks will wait in the sqs).
we will loose only the messages that are in a working process.
if a worker dies during a task his message will return into the share sqs queue of the workers after the visibility Time out and other
worker can take up his place and complete the task.

Threads:
we invested a lot of time to think about concurrency and we come up with the following: 
only the manager should contains threads and we have 2 threads running in the background all the time polling messages from the sqs queues
(one on messages from the local applications and one on messages from the workers) 
then each of those threads is responsible for 1 active object and when nessesary runs it with a fixed thread pool (limit to 10) 
- the manager is responsible for the reducer and activates him when on every task thats he get from the applications this way
  the manager can handle many tasks becouse its is only job ( the reducer is responsible for spliting the tasks and forward them to the workers) 
- the collecter is responsible for the sender and activate him each time all the sub tasks of a specific task are completed
  (the sender combine all the sub tasks and create a summary file wich is sending to the application in return)
  as before it let the handling of a complete task to be as fast as possible becouse all the collecter job is to accumulate the complete tasks of the workers.
*** we are aware to the fact that we can improve the scalability a bit more if we will activate k collecters/manager class with a thread pool.
    lets gets that this application is a real application in the real world with real users - we can estimate the amount of the users and accourding to this estimation
    we can run k managers and collecter classes so the application will be more scalable.


Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.
sure.

Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
done.

Did you manage the termination process? Be sure all is closed once requested!
everything is terminated when requested :)

Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
we taking care about the limit of the ec2 node (20) and make sure we dont start more then 19 workers.
and we wrote before if there will be many users we can run the manager on a strong computers with many cores and activate more threads. 


Are all your workers working hard? 
the answer to that question is mostly NO. 
sometimes some of the workers complete all the tasks before the others even started. 
*if we have already runing workers (from a past task perhaps) then the work is more equally divided.

are you sure you understand what distributed means? Is there anything in your system awaiting another?
noting in our system is awaiting for another (except for waiting for answers) 
all the work is dvided properly and everything work simultaneously.
*we use distributed computing and distributed storeage.

################################################# output file explaing ##################################################################

we choose to show on the output file only the asteroids wich have their is_potentially_hazardous_asteroid flag as 'true'.


#########################################################################################################################################






