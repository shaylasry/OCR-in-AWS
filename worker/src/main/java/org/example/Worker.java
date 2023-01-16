package org.example;

import com.asprise.ocr.Ocr;
import com.asprise.ocr.OcrException;
import org.example.AWS.SQS;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;


import static org.example.AWS.SQS.createQueue;

public class Worker {
    static SqsClient sqs;
    static Ec2Client ec2;

    public static void main(String[] args) {
        makeAWSClients();
        String managerToWorkerQueue = createQueue(sqs, "managerToWorkerQueue");
        String workerToManagerQueue = createQueue(sqs, "workerToManagerQueue");

        //start ocr
        Ocr.setUp(); // one time setup
        Ocr ocr = new Ocr(); // create a new OCR engine
        ocr.startEngine("eng", Ocr.SPEED_FASTEST); // English

        List<Message> messages = SQS.receiveMessage(sqs, managerToWorkerQueue);
        while (!messages.isEmpty()) {

            Message message = messages.get(0);
            String [] messageFromManager = splitManagerMessage(message.body());
            String messageFromManagerBody = messageFromManager[0];
            String messageFromManagerKey = messageFromManager[1];


            //get the url from message
            URL myUrl = null;
            try {
                myUrl = new URL(messageFromManagerBody);
            } catch (MalformedURLException ignored) {
            }


            String OCRResult = "OCR Failed";
            //start decipher
            try {
                OCRResult = ocr.recognize(new URL[] {myUrl}, Ocr.RECOGNIZE_TYPE_ALL, Ocr.OUTPUT_FORMAT_PLAINTEXT, Ocr.PROP_IMG_PREPROCESS_CUSTOM_CMDS
                        ,Ocr.PROP_IMG_PREPROCESS_CUSTOM_CMDS);
                System.out.println(OCRResult);
                if (OCRResult.equals("") || OCRResult.matches(".*\\s.*")) {
                    OCRResult = "OCR Failed";
                    System.out.println("new ocr :   " +OCRResult);
                }
            } catch (OcrException ignored) {
            }


            //make outgoing message
            String outgoingMessage = messageFromManagerKey + "\n";
            //add the url address then \n so when we take it from the file we can take both
            outgoingMessage += "<p>" + "\n";
            outgoingMessage += "<img src=\"" + messageFromManagerBody + "\"><br>\n";
            outgoingMessage += OCRResult + "\n";
            outgoingMessage += "</p>" + "\n";


            SQS.sendMessage(sqs, workerToManagerQueue, outgoingMessage);
            SQS.deleteMessage(sqs, managerToWorkerQueue, message);
            messages = SQS.receiveMessage(sqs, managerToWorkerQueue);
        }

        ocr.stopEngine();
        Runtime runtime = Runtime.getRuntime();
        try
        {
            System.out.println("Shutting down the PC after 0 seconds.");
            runtime.exec("sudo shutdown -P now");
            System.out.println("Shutting down the PC after 0 seconds.");

        }
        catch(IOException e)
        {
            System.out.println("Exception: " +e);
        }
        System.exit(0);
    }


    public static String[] splitManagerMessage(String localAppMessage){
        return localAppMessage.split("\\n");
    }

    public static void makeAWSClients() {
        Region region = Region.US_EAST_1;

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
