package org.example;
import org.example.AWS.ECC;
import org.example.AWS.S3;
import org.example.AWS.SQS;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.AWS.SQS.createQueue;

public class LocalApplication {


    static final String AMI_id = "";
    static String bucketName = ""; //create bucket in s3 and fill up the bucket name
    static S3Client s3;
    static SqsClient sqs;
    static Ec2Client ec2;




    public static String maxFilesPerWorker = "10";


    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Make sure to pass InputFile OutputFile n [terminate]");
            return;
        }

        String filePath = args[0];
        String outputFileName = args[1];

        //if we get just name of file add .html
        String [] outputFileNameToArr = outputFileName.split("\\.");
        if (!outputFileNameToArr[outputFileNameToArr.length - 1].equals("html")){
            outputFileName += ".html";
        }

        maxFilesPerWorker = args[2];

        boolean terminate = args.length > 3 && args[3].equals("[terminate]");
        //create all the required client we should work with
        System.out.println(terminate);
        makeAWSClients();

        //this queue is individual for each local app and after creation local app sends the url to manager with the input location message
        String queueName = String.valueOf(System.nanoTime());
        String managerToLocalAppQueue = createQueue(sqs, queueName);
        //this queue is unique and each one of the local app send message to manger through it.
        String localAppsToManagerQueue = createQueue(sqs, "localAppsToManagerQueue");
        //we decided that we will send url so we should change the return String of putS3Object
        String localAppMessage = "";
        if (terminate) {
            System.out.println("isTerminate");
            localAppMessage += "terminate\n";
        }
        localAppMessage += S3.putS3Object(s3,bucketName,filePath,queueName);
        localAppMessage += "\n";
        localAppMessage += managerToLocalAppQueue;
        System.out.println(localAppMessage);
        //send the combined message to manager
        if(!ECC.check_manager(ec2)){
            ECC.createInstance(ec2, AMI_id, "manager", bucketName);
        }
        SQS.sendMessage(sqs, localAppsToManagerQueue, localAppMessage);


        List<Message> messages = SQS.receiveMessage(sqs, managerToLocalAppQueue);//assumes n is 5
        while(messages.isEmpty()) {
            if (!ECC.check_manager(ec2)){ //fill in the ? that if there is no manage we drop the loop
                try {
                    TimeUnit.SECONDS.sleep(10);
                }
                catch (Exception e)
                {
                    System.out.println("### dont wake me up");
                }
                messages = SQS.receiveMessage(sqs, managerToLocalAppQueue);
                if (!messages.isEmpty()){
                    break;
                }
                ECC.terminateWorkers(ec2);
                SQS.deleteSQSQueue(sqs, "managerToWorkerQueue");
                SQS.deleteSQSQueue(sqs, "workerToManagerQueue");
                SQS.deleteSQSQueue(sqs, queueName);
                SQS.deleteSQSQueue(sqs, "localAppsToManagerQueue");
                S3.deleteS3Object(s3,bucketName,queueName);
                System.out.println("Manager had a problem, try again");
                System.exit(1);

            }
            messages = SQS.receiveMessage(sqs, managerToLocalAppQueue);
        }
        Message message=messages.get(0);
        SQS.deleteMessage(sqs,managerToLocalAppQueue,message);
            //add try and ctach so if we can't get the file print that we filed to get the file and exit
            String outputFileUrl = S3.getS3Object(s3, bucketName, message.body());
            S3.deleteS3Object(s3, bucketName, message.body());
            createHTMLString(outputFileUrl, outputFileName);
        System.out.println("delete queue");
        SQS.deleteSQSQueue(sqs, queueName);
        System.exit(0);
    }


    public static void makeAWSClients() {
        Region region = Region.US_EAST_1;
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();

        //s3
        s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        S3.createBucket(s3, bucketName);

        //ec2
        ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        //sqs
        sqs = SqsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();

    }


    public static void createHTMLString(String summaryFileAsString , String outputFile) {
        String startHTML = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>OCR</title>\n" +
                "</head><body>\n";
        String closeHTML = "</body></html>";
        FileWriter fWriter = null;
        BufferedWriter writer = null;
        try {
            //create file writer
            fWriter = new FileWriter(outputFile);
            writer = new BufferedWriter(fWriter);

            writer.write(startHTML);
            writer.newLine();
            writer.write(summaryFileAsString);
            writer.newLine();
            writer.write(closeHTML);
            writer.close(); //make sure you close the writer object
        } catch (Exception e) {
            //catch any exceptions here
        }

    }
}
