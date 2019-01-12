package com.example.zhangbaokui.rtp;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kui
 * @date on 2019/1/7 18:28
 */
public class DataHandle {

    public static final int SIZE = 1400;

    public static  DataPackge splite(byte[] data,int length){
        DataPackge packge = new DataPackge();
        boolean[] markers;
        if (length<=SIZE){
            byte[][] result = new byte[1][];
            byte[] r = new byte[length];
            markers = new boolean[1];
            markers[0] = true;
            System.arraycopy(data,0,r,0,length);
            result[0] = r;
            packge.data = result;
            packge.markers = markers;
            return packge;
        }
        List<byte[]> byteList = new ArrayList<>();
        int pos = 0;
        int newByteSize;
        int leftData = 0;
        do {
        	leftData = length-pos;
            newByteSize = leftData>SIZE?SIZE:leftData;
            byte[] temp = new byte[newByteSize];
            System.arraycopy(data, pos, temp, 0, newByteSize);
            byteList.add(temp);
            pos+=newByteSize;
        }while(pos!=length);
        byte[][] bytes = new byte[byteList.size()][];
        markers = new boolean[byteList.size()];
        for (int i=0;i<byteList.size();i++){
            bytes[i] = byteList.get(i);
            markers[i] = i==(byteList.size()-1);
        }
        packge.data = bytes;
        packge.markers = markers;
        return packge;
    }
    
    
    public static byte[] com(byte[][] bytes) {
    	int size = SIZE*(bytes.length - 1);
    	size+=bytes[bytes.length-1].length;
    	byte[] result = new byte[size];
    	int pos = 0;
    	for(int i=0;i<bytes.length;i++) {
    		System.arraycopy(bytes[i], 0, result, pos, bytes[i].length);
    		pos+=bytes[i].length;
    	}
    	return result;
    }
    

}
