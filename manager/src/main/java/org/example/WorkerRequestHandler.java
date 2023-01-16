package org.example;

import org.example.AWS.S3;
import org.example.AWS.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.example.Manager.*;

public class WorkerRequestHandler implements Runnable{
    String workerToManagerQueue;

    public  WorkerRequestHandler(String workerToManagerQueue) {
        this.workerToManagerQueue = workerToManagerQueue;
    }

    @Override
    public void run() {

        while((localAppsResultMap.size() > 0) || !terminated.get()) {
            List<Message> messagesFromWorker = SQS.receiveMessage(sqs, workerToManagerQueue);

            if (!messagesFromWorker.isEmpty()) {
                System.out.println("in 11");
                Message message = messagesFromWorker.get(0);
                String[] workerResult = message.body().split("\\n");
                SQS.deleteMessage(sqs, workerToManagerQueue, message);
                numSizeQueue.getAndDecrement();


                //resultKey is the mangerToLocalApp url address which represent the specific localApp key in the hashMap
                String resultKey = workerResult[0];
                String resultBody = message.body().substring(resultKey.length() + 1);

                //get the current OCR deciphered string list and add the new string
                OCRResult OCRResultList = localAppsResultMap.get(resultKey);
                OCRResultList.OCRStringList.add(resultBody);

                //if we finished to decipher for this queue create the summary file and after that remove it from the hashmap
                if (OCRResultList.isFull()) {
                    createAndSendSummaryFile(OCRResultList.OCRStringList, resultKey);
                    localAppsResultMap.remove(resultKey);
                }
            }
        }
    }

    public static void createAndSendSummaryFile(ConcurrentLinkedQueue<String> summaryList, String managerToLocalAppQueue) {
        try {
            //create new file to enter the data to
            File summaryFile = new File("summaryFile" + System.nanoTime() + ".txt");

            if (summaryFile.createNewFile()) {
                System.out.println("Summary file created: " + summaryFile.getName());

                FileWriter writeToFile = new FileWriter(summaryFile);
                for (String imageOcr : summaryList) {
                    writeToFile.write(imageOcr + "\n");
                }
                writeToFile.close();

                //upload file to s3
                String fileIdInS3 = S3.putS3Object(s3, bucketName, summaryFile.getName());
                SQS.sendMessage(sqs, managerToLocalAppQueue, fileIdInS3);

                if (summaryFile.delete()) {
                    System.out.println("Deleted the file from local repository: " + summaryFile.getName());
                } else {
                    System.out.println("Failed to delete the file.");
                }

            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
