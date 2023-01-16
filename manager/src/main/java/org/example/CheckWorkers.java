package org.example;

import org.example.AWS.ECC;

import java.util.concurrent.TimeUnit;

import static org.example.Manager.*;

public class CheckWorkers implements Runnable{

    public void run() {
        while ((localAppsResultMap.size() > 0) || !terminated.get()) {
            try {
                TimeUnit.SECONDS.sleep(5);
            }catch (Exception e) {
                System.out.println("check workers won't wake me up");
            }

            int managerToWorkerQueueSize = numSizeQueue.get();
            //n input is numOfTasksPerWorker
            //lets call it m - number of current task in queue is managerToWorkerQueueSize

            //so to get total number we need to calculate m/n
            int numOfWorkersToCreate = managerToWorkerQueueSize / numOfTasksPerWorker;
            //now if we get remains we should round up
            if (managerToWorkerQueueSize % numOfTasksPerWorker != 0) {
                numOfWorkersToCreate++;
            }
            //make sure that the biggest number we want to ask will be the maxWorkers
            numOfWorkersToCreate = Math.min(maxWorkers, numOfWorkersToCreate);

            int numOfActiveWorkers = ECC.sumWorkers(ec2);
            //if numOfActiveWorkers is less than maxWorkers we can add workers and we take the min between both
            if (numOfActiveWorkers < numOfWorkersToCreate) {
                numOfWorkersToCreate = numOfWorkersToCreate - numOfActiveWorkers;
                for (int i = 0; i < numOfWorkersToCreate; i++) {
                    ECC.createInstance(ec2, amiId, "worker", bucketName);
                }
            }
        }
    }
}
