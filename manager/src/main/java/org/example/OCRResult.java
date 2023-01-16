package org.example;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OCRResult {

    ConcurrentLinkedQueue<String> OCRStringList;
    int inputSize;

    public OCRResult(int inputSize){
        this.OCRStringList = new ConcurrentLinkedQueue<>();
        this.inputSize = inputSize;

    }

    public boolean isFull(){
        return this.inputSize == this.OCRStringList.size();
    }
}
