package org.example.AWS;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ECC {


    public static String createInstance(Ec2Client ec2, String amiId, String name, String bucketName) {
        String userData = "";
        userData = userData + "#!/bin/bash" + "\n";
        userData = userData + "cd /home" + "\n";
        userData = userData + "pwd\n";
        userData = userData + " java -version\n";
        userData = userData + "sudo aws s3 cp s3://" + bucketName + "/worker-1.0-SNAPSHOT-jar-with-dependencies.jar .\n";
        userData = userData + "pwd\n";
        userData = userData + "ls\n";
        userData = userData + "sudo java -jar worker-1.0-SNAPSHOT-jar-with-dependencies.jar &\n";


        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T1_MICRO)
                .maxCount(1)
                .minCount(1)
//                .iamInstanceProfile(IamInstanceProfileSpecification.builder().build())
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .instanceInitiatedShutdownBehavior("terminate")
                .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
                .build();


        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 Instance %s based on AMI %s \n",
                    instanceId, amiId);

            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }



    public static boolean terminateInstances(Ec2Client ec2Client, List<String> instancesID) {
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest
                .builder()
                .instanceIds(instancesID)
                .build();
        try {
            ec2Client.terminateInstances(terminateRequest);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    public static int sumWorkers(Ec2Client ec2) {
        int count = 0;
        Filter tagFilter = Filter.builder().name("tag:Name").values("worker").build();
        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running", "pending")
                .build();
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(tagFilter,stateFilter).build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                count++;
            }
        }
        System.out.println(count);
        return count;
    }

    public static void terminateWorkers(Ec2Client ec2) {
        List<String> workerList = new ArrayList<String>();
        Filter tagFilter = Filter.builder().name("tag:Name").values("worker").build();
        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running", "pending")
                .build();
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(tagFilter,stateFilter).build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                System.out.println(instance.state().name());
                workerList.add(instance.instanceId());
            }
        }
        System.out.println("there were " + workerList.size() + " workers terminated");
        terminateInstances(ec2,workerList);
    }

    public static void terminateManager(Ec2Client ec2) {// return true of has manager\
        List<String> managerList = new ArrayList<String>();
        Filter tagFilter = Filter.builder().name("tag:Name").values("manager").build();
        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running", "pending")
                .build();
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(tagFilter, stateFilter).build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                managerList.add(instance.instanceId());
            }
        }
        terminateInstances(ec2, managerList);
    }
}
