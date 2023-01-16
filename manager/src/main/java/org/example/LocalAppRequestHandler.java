package org.example;

import org.example.AWS.ECC;
import org.example.AWS.S3;
import org.example.AWS.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.Manager.*;

public class LocalAppRequestHandler implements Runnable{
    String localAppsToManagerQueue;
    String managerToWorkerQueue;
    String bucketName;

    public LocalAppRequestHandler(String localAppsToManagerQueue, String managerToWorkerQueue, String bucketName){
        this.localAppsToManagerQueue = localAppsToManagerQueue;
        this.managerToWorkerQueue = managerToWorkerQueue;
        this.bucketName = bucketName;
    }

    @Override
    public void run() {

        while ((localAppsResultMap.size() > 0) || !terminated.get()) {
            List<Message> messagesFromLocalApp = SQS.receiveMessage(sqs,localAppsToManagerQueue);//maybe pdont create List everytime?

            if(!messagesFromLocalApp.isEmpty()) {// has jobs
                System.out.println("message is not empty, get the message from queue");
                //get message from localApp and split it
                Message message = messagesFromLocalApp.get(0);
                String [] localAppMessage = splitLocalAppMessage(message.body());
                SQS.deleteMessage(sqs, localAppsToManagerQueue,message);

                //check message validation
                if (localAppMessage.length < 1) {
                    System.out.println("Error, local app message is empty");
                }

                boolean isTermination = false;
                String localMessageType = localAppMessage[0];
                String fileKey;
                String localAppQueueUrl;
                System.out.println("dis is local message type: "+localMessageType);
                if (localMessageType.equals("terminate")) {
                    isTermination = true;
                    fileKey = localAppMessage[1];
                    localAppQueueUrl = localAppMessage[2];
                } else {
                    fileKey = localAppMessage[0];
                    localAppQueueUrl = localAppMessage[1];
                }
                //if no termination so we take the data from the message and pass it to handle input file method
                handleInputFile(fileKey, localAppQueueUrl, managerToWorkerQueue);
                System.out.println("is termination"+localMessageType);
                if (isTermination) {
                        handleTermination();
                }
            }
        }

    }

    public static void handleTermination(){
        System.out.println("i am hadeling termination");
        terminated.set(true);
        System.out.println("localAppsResultMap:"+localAppsResultMap.size());
        while (localAppsResultMap.size() > 0) {
            try {
                TimeUnit.SECONDS.sleep(2);
            }
            catch (Exception e)
            {
                System.out.println("### dont wake me up");
            }
        }
        try {
            TimeUnit.SECONDS.sleep(30);
        }
        catch (Exception e)
        {
            System.out.println("### dont wake me up");
        }
        System.out.println("localAppsResultMap:"+localAppsResultMap.size());
        System.out.println("i have passed the while");
        ECC.terminateWorkers(ec2);
        SQS.deleteSQSQueue(sqs, "managerToWorkerQueue");
        SQS.deleteSQSQueue(sqs, "workerToManagerQueue");
        SQS.deleteSQSQueue(sqs, "localAppsToManagerQueue");
        ECC.terminateManager(ec2);

    }

    public static void
    handleInputFile(String inputFileKey, String localAppQueueUrl, String managerToWorkerQueue) {

        String inputFileContent = S3.getS3Object(s3, Manager.bucketName, inputFileKey);//assume message.body is key
        String [] imagesUrlsArray = inputFileContent.split("\\n");

        S3.deleteS3Object(s3, Manager.bucketName, inputFileKey);
        //maybe make a second function and return urls by the first
        OCRResult ocrResultList = new OCRResult(imagesUrlsArray.length);//maybe do like a for loop with a queue add
        localAppsResultMap.put(localAppQueueUrl, ocrResultList);
        System.out.println("local app key in handle input is:\n" + localAppQueueUrl);
        System.out.println(localAppsResultMap.get(localAppQueueUrl).isFull());

        //generate new message and append id of localAppQueueUrl which is also the key in the hashmap and send the message to worker queue
        for (int i = 0; i < imagesUrlsArray.length; i++) {
            imagesUrlsArray[i] += "\n";
            imagesUrlsArray[i] += localAppQueueUrl;
            SQS.sendMessage(sqs, managerToWorkerQueue, imagesUrlsArray[i]);
        }
        numSizeQueue.getAndAdd(imagesUrlsArray.length);



    }

    public static String[] splitLocalAppMessage(String localAppMessage){
        return localAppMessage.split("\\n");
    }


}
