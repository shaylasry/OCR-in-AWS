Instructions to run program:

1\. The program ran with pre-builed AMI created from linux default AMI of amazon after we installed Java 1.8 and Maven 3.8.0 to run the program, this AMI no longer exist.

For convinience you can use the default linex AMI and add installation commands in each of the createInstace methods ECC.java file.

locations:

1\. assignment1/localApplication/src/main/java/org.example/LocalApplicationAWS/ECC.java

2\. assignment1/manager/src/main/java/org.example/Manager/AWS/ECC.java

Enter the AMI-Id in:

1\. assignment1/localApplication/src/main/java/org.example/LocalApplication line 22:

change AMI_id instance to new AMI-id

2\. assignment1/manager/src/main/java/org.example/Manager line 29:

change AMI_id instance to new AMI-id

2\. Create jar file for each module

For manager go first to manager directory and than call mvn package

For worker go first to worker directory and than call mvn package

For localApplication go first to localApplication directory

(NOTICE not to go to the assignment1.src.local application, there is localApplication dir in assignment1 where you should go)

and than call mvn package

\*\*For all jar files use the one with jar-with-dependencies suffix.

\*\*Due to internet speed issues we upload the files from advance and not upload and delete it each time

upload and delete can part of the code the same way we upload the input file to the bucket in s3

3\. Create bucket in s3, :

1\. assignment1/localApplication/src/main/java/org.example/LocalApplication line 24:

change bucketName instance to new bucket name

2\. assignment1/manager/src/main/java/org.example/Manager line 30:

change bucketName instance to new bucket name

Upload manager and worker jar(with jar-with-dependencies suffix) files to the buccket with the same name.

4\. Run LocalApplication jar with the required args:

java -jar yourjar.jar inputFileName outputFileName n [terminate]

if outpuFileName don't include html in the end we will add it to the ouputFileName

Program Flow:

1\. Local application takes input file and upload it to S3 and checks if there is running manger, if not open one.

2\. Manager download the input file, open workers according the queue size and send the url to the workers

For each local application manager create new OCR Result object that contains concurrent list for the deciphered data

Key is the url of the manager to local app queue.

3\. Workers take urls from queue decipher it and return the decipher text to manger.

4\. Manager add the decipher data and when get all the data manager will create summary file and return it to local application

5\. local application creats html file and close queue.

Local application:

\*\*While waiting for manager response local app checks if there is active manager and if not it will shut down all instances and queues

Manager:

\*\*Manager will check if the num of activate workers is updated each 5 seconds and if not it will create new workers

only if the hashmap is not empty which means there is still data to decipher.

\*\*Manager will work as multithread, localapp comunication only happen twice, get messeage and sent message so

we only run 1 thread for this comunication.

For worker manager comunication there are a lot of message going in an out so we created 3 thread (number can be change before creating the jar)

to maintain good comunication.

Worker:

\*\*When worker can't get more messages from queue it will close itself to maintain low number of workers

\*\*Limit num of workers to 10 (11 instances can be launched at once) so we won't pass 19.

\*\*If OCR package couldn't decipher the image, output will be "OCR failed"
