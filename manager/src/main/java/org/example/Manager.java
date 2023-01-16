package org.example;


import org.example.AWS.ECC;
import org.example.AWS.S3;
import org.example.AWS.SQS;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.AWS.SQS.*;


public class Manager {
    //sqs service
    static final String amiId = "";
    static String bucketName = "";
    static S3Client s3;
    static SqsClient sqs;
    static Ec2Client ec2;
 // create bucket in s3 and fill up the bucket name

    //workers instances
    static int numOfTasksPerWorker; // init as large number for now so we won't open many workers
    static final int maxWorkers = 10;
    public static AtomicInteger numSizeQueue = new AtomicInteger(0);
    static Integer numOfWorkers = 0;

    //each local application queue usl is the key and OCR result conatins the deciphered images
    public static HashMap<String, OCRResult> localAppsResultMap = new HashMap();
    //key is the local application that sent input file and value is the String to return so we can make file


    public static final int numOfManagerWorkerThreads = 3;
    public static AtomicBoolean terminated = new AtomicBoolean(false);




    public static void main(String[] args){
        makeAWSClients();
        String localAppsToManagerQueue = createQueue(sqs, "localAppsToManagerQueue");
        String managerToWorkerQueue = createQueue(sqs, "managerToWorkerQueue");
        String workerToManagerQueue = createQueue(sqs, "workerToManagerQueue");

        System.out.println("Finished to create queues, start receiving messages");

        numOfTasksPerWorker = Integer.parseInt(args[0]);

        LocalAppRequestHandler localAppHandler = new LocalAppRequestHandler(localAppsToManagerQueue, managerToWorkerQueue, bucketName);
        WorkerRequestHandler workerHandler = new WorkerRequestHandler(workerToManagerQueue);
        CheckWorkers checkWorkers = new CheckWorkers();


        //use executor
        ExecutorService executorService = Executors.newFixedThreadPool(2 + numOfManagerWorkerThreads);
        executorService.execute(localAppHandler);
        executorService.execute(checkWorkers);
        for(int i = 0; i < numOfManagerWorkerThreads; i++) {
            executorService.execute(workerHandler);
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
        }
        
    }



    public static void makeAWSClients() {
        Region region = Region.US_EAST_1;

        //s3
        s3 = S3Client.builder()
                .region(region)
                .build();

        S3.createBucket(s3, bucketName);

        //ec2
        ec2 = Ec2Client.builder()
                .region(region)
                .build();

        //sqs
        sqs = SqsClient.builder()
                .region(region)
                .build();

    }

}